package com.rbac.rbac0;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=${RBAC_TEST_DB_URL}",
        "spring.datasource.username=${RBAC_TEST_DB_USERNAME}",
        "spring.datasource.password=${RBAC_TEST_DB_PASSWORD:}",
        "rbac.bootstrap.username=integration-admin",
        "rbac.bootstrap.password=integration-test-password"
})
@AutoConfigureMockMvc
class AuthorizationApiTest {

    private static final String API = "/api/v1";
    private static final String ASSIGNMENTS = API + "/user-role-assignments";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate database;

    @Test
    void businessPermissionUsesCurrentGrantsAndKeepsPermissionsFromOtherRoles() throws Exception {
        var admin = admin();
        String suffix = unique();
        long department = department(admin, "工单部", null);
        String username = "ticket-" + suffix;
        var employee = user(admin, username, department, false);
        long employeeId = userId(employee);
        var employeeToken = login(username, employee.path("initialPassword").asText());
        long app = id(create(admin, API + "/applications", Map.of("name", "工单-" + suffix)));
        long otherApp = id(create(admin, API + "/applications", Map.of("name", "档案-" + suffix)));
        String appToken = "app-fixture-" + suffix;
        database.update("UPDATE sys_application SET token_hash = UNHEX(SHA2(?, 256)) WHERE id = ?", appToken, app);

        String permissions = API + "/applications/" + app + "/permissions";
        long menu = id(create(admin, permissions, Map.of("name", "工单", "type", "MENU", "code", "ticket:view")));
        long button = id(create(admin, permissions,
                Map.of("name", "关闭", "type", "BUTTON", "code", "ticket:close", "parentId", menu)));
        long first = role(admin, "受理-" + suffix, List.of(menu, button));
        long second = role(admin, "复核-" + suffix, List.of(menu, button));

        check(app, appToken, employeeId, "ticket:close").andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(false));
        check(app, "bad-token", employeeId, "ticket:close").andExpect(status().isUnauthorized());
        check(otherApp, appToken, employeeId, "ticket:close").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPLICATION_MISMATCH"));
        mvc.perform(body(post(checkPath(app)).header("Authorization", "Bearer " + admin),
                Map.of("userId", employeeId, "permissionCode", "ticket:close"))).andExpect(status().isUnauthorized());
        mvc.perform(bearer(get(API + "/me/permissions"), employeeToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.businessRoles").isEmpty())
                .andExpect(jsonPath("$.applications[?(@.applicationId == " + app + ")].menus[0]").doesNotExist());

        replace(admin, employeeId, List.of(first, second)).andExpect(status().isOk());
        mvc.perform(bearer(get(API + "/me/permissions"), employeeToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.businessRoles.length()").value(2))
                .andExpect(jsonPath("$.applications[?(@.applicationId == " + app + ")].menus[0].buttons[*].code")
                        .value(org.hamcrest.Matchers.contains("ticket:close")));
        check(app, appToken, employeeId, "ticket:close").andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(true));
        check(app, appToken, employeeId, "ticket:CLOSE").andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(false));

        replace(admin, employeeId, List.of(second)).andExpect(status().isOk());
        check(app, appToken, employeeId, "ticket:close").andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(true));
        mvc.perform(body(bearer(patch(permissions + "/" + button), admin), Map.of("code", "ticket:finish")))
                .andExpect(status().isOk());
        check(app, appToken, employeeId, "ticket:close").andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(false));
        check(app, appToken, employeeId, "ticket:finish").andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(true));
        replace(admin, employeeId, List.of()).andExpect(status().isOk()).andExpect(jsonPath("$.roleIds").isEmpty());
        check(app, appToken, employeeId, "ticket:finish").andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(false));
        replace(admin, employeeId, List.of(second)).andExpect(status().isOk());
        mvc.perform(body(bearer(patch(API + "/users/" + employeeId), admin), Map.of("status", "DISABLED")))
                .andExpect(status().isOk());
        check(app, appToken, employeeId, "ticket:finish").andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(false));
        check(app, appToken, 9_007_199_254_740_991L, "ticket:finish")
                .andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(false));
    }

    @Test
    void batchValidatesAllUsersBeforeWritingAndRecordsOnlyActualChanges() throws Exception {
        var admin = admin();
        String suffix = unique();
        long root = department(admin, "授权本部", null);
        long child = department(admin, "下级组", root);
        long other = department(admin, "外部门", null);
        String managerName = "grant-manager-" + suffix;
        var manager = user(admin, managerName, root, true);
        long managerId = userId(manager);
        long staff = userId(user(admin, "grant-staff-" + suffix, child, false));
        long outside = userId(user(admin, "grant-outside-" + suffix, other, false));
        long role = role(admin, "授权-" + suffix, List.of());
        long retainedRole = role(admin, "保留-" + suffix, List.of());
        var managerToken = login(managerName, manager.path("initialPassword").asText());
        long managementRole = database.queryForObject(
                "SELECT id FROM sys_role WHERE role_type = 'DEPARTMENT_ADMIN'", Long.class);

        batch(managerToken, "GRANT", List.of(staff, outside), List.of(role))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("OUTSIDE_MANAGEMENT_SCOPE"));
        assertThat(database.queryForObject("SELECT COUNT(*) FROM sys_user_role WHERE role_id = ?", Long.class, role)).isZero();
        batch(managerToken, "GRANT", List.of(staff), List.of(managementRole)).andExpect(status().isBadRequest());
        replace(managerToken, outside, List.of(role)).andExpect(status().isForbidden());

        replace(admin, staff, List.of(retainedRole)).andExpect(status().isOk());
        batch(managerToken, "GRANT", List.of(staff, managerId), List.of(role))
                .andExpect(status().isOk()).andExpect(jsonPath("$.affectedUsers").value(2));
        batch(managerToken, "GRANT", List.of(staff, managerId), List.of(role))
                .andExpect(status().isOk()).andExpect(jsonPath("$.affectedUsers").value(0));
        var snapshots = database.queryForList("""
                SELECT target.department_id_at_event
                FROM sys_operation_log log JOIN sys_operation_log_target target ON target.operation_log_id = log.id
                WHERE log.action = '批量分配角色' AND log.scope = 'USER_SCOPE' AND log.actor_user_id = ?
                ORDER BY target.department_id_at_event
                """, Long.class, managerId);
        assertThat(snapshots).containsExactly(root, child);
        batch(managerToken, "REVOKE", List.of(staff, managerId), List.of(role))
                .andExpect(status().isOk()).andExpect(jsonPath("$.affectedUsers").value(2));

        mvc.perform(bearer(get(API + "/users/" + staff), managerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.businessRoles.length()").value(1))
                .andExpect(jsonPath("$.businessRoles[0].id").value(retainedRole));
        replace(managerToken, managerId, List.of()).andExpect(status().isOk());
        mvc.perform(bearer(get(API + "/me"), managerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.managementRole").value("DEPARTMENT_ADMIN"));
    }

    private ResultActions replace(String token, long userId, List<Long> roleIds) throws Exception {
        return mvc.perform(body(bearer(put(API + "/users/" + userId + "/roles"), token), Map.of("roleIds", roleIds)));
    }

    private ResultActions batch(String token, String action, List<Long> users, List<Long> roles) throws Exception {
        return mvc.perform(body(bearer(patch(ASSIGNMENTS), token), Map.of("action", action, "userIds", users, "roleIds", roles)));
    }

    private ResultActions check(long app, String token, long userId, String code) throws Exception {
        return mvc.perform(body(post(checkPath(app)).header("X-Application-Token", token),
                Map.of("userId", userId, "permissionCode", code)));
    }

    private static String checkPath(long app) {
        return API + "/applications/" + app + "/permission-checks";
    }

    private long role(String token, String name, List<Long> permissions) throws Exception {
        return id(create(token, API + "/roles", Map.of("name", name, "permissionIds", permissions)));
    }

    private long department(String token, String name, Long parent) throws Exception {
        var input = json.createObjectNode().put("name", name);
        if (parent == null) {
            input.putNull("parentId");
        } else {
            input.put("parentId", parent);
        }
        return id(create(token, API + "/departments", input));
    }

    private JsonNode user(String token, String username, long department, boolean manager) throws Exception {
        return create(token, API + "/users", Map.of("username", username, "name", username,
                "departmentId", department, "departmentAdministrator", manager));
    }

    private String admin() throws Exception {
        return login("integration-admin", "integration-test-password");
    }

    private String login(String username, String password) throws Exception {
        return readCreated(body(post(API + "/sessions"), Map.of("username", username, "password", password)))
                .path("accessToken").asText();
    }

    private JsonNode create(String token, String path, Object input) throws Exception {
        return readCreated(body(bearer(post(path), token), input));
    }

    private JsonNode readCreated(MockHttpServletRequestBuilder request) throws Exception {
        return json.readTree(mvc.perform(request).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsByteArray());
    }

    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object input) throws Exception {
        return request.contentType("application/json").content(json.writeValueAsBytes(input));
    }

    private static long id(JsonNode value) {
        return value.path("id").asLong();
    }

    private static long userId(JsonNode value) {
        return id(value.path("user"));
    }

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
