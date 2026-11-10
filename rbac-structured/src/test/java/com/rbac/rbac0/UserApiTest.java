package com.rbac.rbac0;

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
class UserApiTest {

    private static final String API = "/api/v1";
    private static final String USERS = API + "/users";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate database;

    @Test
    void userCreationReturnsTheInitialPasswordOnceAndConflictingNamesAreRejected() throws Exception {
        var admin = admin();
        long department = department(admin, "初始密码测试", null);
        String username = "employee-" + unique();
        var input = Map.of("username", username, "name", "林晓", "departmentId", department);
        var response = mvc.perform(body(bearer(post(USERS), admin), input))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.user.status").value("ACTIVE"))
                .andExpect(jsonPath("$.user.departmentAdministrator").value(false))
                .andReturn().getResponse();
        var created = json.readTree(response.getContentAsByteArray());
        var password = created.path("initialPassword").asText();
        long id = created.path("user").path("id").asLong();
        assertThat(password).isNotBlank();
        assertThat(database.queryForObject("SELECT password_hash FROM sys_user WHERE id = ?", String.class, id))
                .startsWith("$argon2id$").isNotEqualTo(password);
        login(username, password);

        mvc.perform(bearer(get(USERS + "/" + id), admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.initialPassword").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(body(bearer(post(USERS), admin), input))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
        String taken = "taken-" + unique();
        createUser(admin, taken, department, false);
        mvc.perform(body(bearer(patch(USERS + "/" + id), admin), Map.of("username", taken, "name", "未保存的姓名")))
                .andExpect(status().isConflict());
        mvc.perform(bearer(get(USERS + "/" + id), admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("林晓"));
        mvc.perform(body(bearer(patch(USERS + "/" + id), admin), Map.of("username", username + "-new", "name", "林小晓")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(username + "-new"))
                .andExpect(jsonPath("$.name").value("林小晓"));
        mvc.perform(body(bearer(patch(USERS + "/" + id), admin), json.createObjectNode().putNull("name")))
                .andExpect(status().isBadRequest());
        mvc.perform(body(bearer(patch(USERS + "/" + id), admin), Map.of()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void departmentAdministratorCanOnlyReadAndManageTheirOwnSubtree() throws Exception {
        var admin = admin();
        String suffix = unique();
        long root = department(admin, "部门-" + suffix, null);
        long child = department(admin, "下级-" + suffix, root);
        long outside = department(admin, "外部-" + suffix, null);
        var manager = createUser(admin, "manager-" + suffix, root, true);
        var staff = createUser(admin, "staff-" + suffix, child, false);
        var external = createUser(admin, "external-" + suffix, outside, false);
        var managerToken = login("manager-" + suffix, manager.path("initialPassword").asText());
        var staffToken = login("staff-" + suffix, staff.path("initialPassword").asText());

        mvc.perform(bearer(get(USERS).param("keyword", suffix).param("pageSize", "1").param("page", "2"), managerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.page").value(2)).andExpect(jsonPath("$.items[0].id").value(userId(staff)));
        mvc.perform(bearer(get(USERS).param("keyword", "下级-" + suffix)
                        .param("includeDepartmentName", "true").param("departmentId", Long.toString(root)), managerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(userId(staff)));
        mvc.perform(bearer(get(USERS).param("departmentId", Long.toString(outside)), managerToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("OUTSIDE_MANAGEMENT_SCOPE"));
        mvc.perform(bearer(get(USERS + "/" + userId(external)), managerToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("OUTSIDE_MANAGEMENT_SCOPE"));
        mvc.perform(bearer(get(USERS), staffToken)).andExpect(status().isForbidden());
        mvc.perform(body(bearer(post(USERS), managerToken), Map.of("username", "new-" + suffix,
                        "name", "下级员工", "departmentId", child))).andExpect(status().isCreated());
        mvc.perform(body(bearer(post(USERS), managerToken), Map.of("username", "forbidden-" + suffix,
                        "name", "同部门员工", "departmentId", child, "departmentAdministrator", false)))
                .andExpect(status().isForbidden());
        mvc.perform(body(bearer(patch(USERS + "/" + userId(staff)), managerToken), Map.of("departmentId", root)))
                .andExpect(status().isForbidden());
        mvc.perform(body(bearer(patch(USERS + "/" + userId(staff)), managerToken), Map.of("departmentAdministrator", true)))
                .andExpect(status().isForbidden());
    }

    @Test
    void transferKeepsBusinessRolesAndDisablingRevokesExistingSessions() throws Exception {
        var admin = admin();
        long before = department(admin, "调出部门", null);
        long after = department(admin, "调入部门", null);
        String suffix = unique();
        String username = "transfer-" + suffix;
        var created = createUser(admin, username, before, false);
        long id = userId(created);
        var password = created.path("initialPassword").asText();
        var employeeToken = login(username, password);
        var secondToken = login(username, password);
        String roleName = "业务角色-" + suffix;
        database.update("INSERT INTO sys_role (name, role_type) VALUES (?, 'BUSINESS')", roleName);
        database.update("""
                INSERT INTO sys_user_role (user_id, role_id) SELECT ?, id FROM sys_role WHERE name = ?
                """, id, roleName);

        mvc.perform(body(bearer(patch(USERS + "/" + id), admin), Map.of("departmentId", after, "departmentAdministrator", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.department.id").value(after))
                .andExpect(jsonPath("$.businessRoles[0].name").value(roleName))
                .andExpect(jsonPath("$.departmentAdministrator").value(true));
        mvc.perform(bearer(get(API + "/me"), employeeToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.departmentId").value(after))
                .andExpect(jsonPath("$.managementRole").value("DEPARTMENT_ADMIN"));

        mvc.perform(body(bearer(patch(USERS + "/" + id), admin), Map.of("departmentAdministrator", false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.departmentAdministrator").value(false))
                .andExpect(jsonPath("$.businessRoles[0].name").value(roleName));
        mvc.perform(body(bearer(patch(USERS + "/" + id), admin), Map.of("status", "DISABLED")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
        mvc.perform(bearer(get(API + "/me"), employeeToken)).andExpect(status().isUnauthorized());
        mvc.perform(bearer(get(API + "/me"), secondToken)).andExpect(status().isUnauthorized());
        assertThat(database.queryForObject("SELECT COUNT(*) FROM sys_session WHERE user_id = ?", Long.class, id)).isZero();
        assertThat(database.queryForObject("""
                SELECT COUNT(*) FROM sys_operation_log log
                JOIN sys_operation_log_target target ON target.operation_log_id = log.id
                WHERE target.target_id = ? AND target.target_type = 'USER'
                  AND log.scope = 'USER_SCOPE' AND log.action = '停用账号' AND target.department_id_at_event = ?
                """, Long.class, id, after)).isEqualTo(1L);
    }

    private String admin() throws Exception {
        return login("integration-admin", "integration-test-password");
    }

    private String login(String username, String password) throws Exception {
        return readCreated(body(post(API + "/sessions"), Map.of("username", username, "password", password)))
                .path("accessToken").asText();
    }

    private long department(String admin, String name, Long parent) throws Exception {
        var input = json.createObjectNode().put("name", name);
        if (parent == null) {
            input.putNull("parentId");
        } else {
            input.put("parentId", parent);
        }
        return readCreated(body(bearer(post(API + "/departments"), admin), input)).path("id").asLong();
    }

    private JsonNode createUser(String admin, String username, long department, boolean manager) throws Exception {
        return readCreated(body(bearer(post(USERS), admin), Map.of("username", username, "name", username,
                "departmentId", department, "departmentAdministrator", manager)));
    }

    private JsonNode readCreated(MockHttpServletRequestBuilder request) throws Exception {
        return json.readTree(mvc.perform(request).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsByteArray());
    }

    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object payload) throws Exception {
        return request.contentType("application/json").content(json.writeValueAsBytes(payload));
    }

    private static long userId(JsonNode created) {
        return created.path("user").path("id").asLong();
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }
}
