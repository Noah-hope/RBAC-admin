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
class OperationLogApiTest {

    private static final String API = "/api/v1";
    private static final String LOGS = API + "/operation-logs";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate database;

    @Test
    void departmentManagerSearchesAndPaginatesOnlyVisibleHistoricalTargets() throws Exception {
        var admin = login("integration-admin", "integration-test-password");
        String suffix = unique();
        long root = department(admin, "日志本部", null);
        long child = department(admin, "日志下级", root);
        long other = department(admin, "日志外部", null);
        String managerName = "log-manager-" + suffix;
        var manager = user(admin, managerName, root, true);
        String visibleName = "log-visible-" + suffix;
        long visible = user(admin, visibleName, child, false).path("user").path("id").asLong();
        String hiddenName = "log-hidden-" + suffix;
        long hidden = user(admin, hiddenName, other, false).path("user").path("id").asLong();
        String roleName = "日志角色-" + suffix;
        long role = create(admin, "/roles", Map.of("name", roleName, "permissionIds", List.of())).path("id").asLong();

        changeRoles(admin, "GRANT", List.of(visible, hidden), role);
        changeRoles(admin, "REVOKE", List.of(visible), role);
        changeRoles(admin, "REVOKE", List.of(hidden), role);
        mvc.perform(body(bearer(patch(API + "/users/" + visible), admin), Map.of("departmentId", other)))
                .andExpect(status().isOk());

        String keyword = "所选角色：" + roleName;
        assertThat(database.queryForObject("""
                SELECT ABS(TIMESTAMPDIFF(SECOND, MAX(occurred_at), UTC_TIMESTAMP()))
                FROM sys_operation_log WHERE detail = ?
                """, Long.class, keyword)).isLessThan(60L);
        assertThat(database.update("""
                UPDATE sys_operation_log SET occurred_at = '2026-11-10 01:15:00' WHERE detail = ?
                """, keyword)).isEqualTo(3);
        var token = login(managerName, manager.path("initialPassword").asText());

        mvc.perform(bearer(get(LOGS).param("keyword", keyword).param("pageSize", "1"), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].action").value("批量撤销角色"))
                .andExpect(jsonPath("$.items[0].targetNames[0]").value(visibleName));
        mvc.perform(bearer(get(LOGS).param("keyword", keyword).param("pageSize", "1").param("page", "2"), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items[0].action").value("批量分配角色"))
                .andExpect(jsonPath("$.items[0].occurredAt").value("2026-11-10T01:15:00Z"))
                .andExpect(jsonPath("$.items[0].targetNames.length()").value(1))
                .andExpect(jsonPath("$.items[0].targetNames[0]").value(visibleName));
        mvc.perform(bearer(get(LOGS).param("keyword", keyword).param("pageSize", "1").param("page", "3"), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(bearer(get(LOGS).param("keyword", hiddenName), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(bearer(get(LOGS).param("keyword", visibleName), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3));
        mvc.perform(bearer(get(LOGS).param("keyword", "跨部门调动"), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(bearer(get(LOGS).param("keyword", keyword).param("pageSize", "1").param("page", "3"), admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[0].targetNames[0]").value(visibleName))
                .andExpect(jsonPath("$.items[0].targetNames[1]").value(hiddenName));
        mvc.perform(bearer(get(LOGS).param("keyword", roleName), admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(4));
    }

    @Test
    void rejectsRequestsFromOrdinaryUsersAndInvalidPagination() throws Exception {
        var admin = login("integration-admin", "integration-test-password");
        long department = department(admin, "普通用户日志测试", null);
        String username = "log-employee-" + unique();
        var employee = user(admin, username, department, false);
        var token = login(username, employee.path("initialPassword").asText());

        mvc.perform(get(LOGS)).andExpect(status().isUnauthorized());
        mvc.perform(bearer(get(LOGS), token)).andExpect(status().isForbidden());
        mvc.perform(bearer(get(LOGS).param("pageSize", "101"), admin)).andExpect(status().isBadRequest());
        mvc.perform(bearer(get(LOGS).param("keyword", username), admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].actorName").value("总管理员"))
                .andExpect(jsonPath("$.items[0].result").value("SUCCESS"));
    }

    private void changeRoles(String token, String action, List<Long> users, long role) throws Exception {
        mvc.perform(body(bearer(patch(API + "/user-role-assignments"), token),
                Map.of("action", action, "userIds", users, "roleIds", List.of(role)))).andExpect(status().isOk());
    }

    private long department(String admin, String name, Long parent) throws Exception {
        var input = json.createObjectNode().put("name", name);
        if (parent == null) {
            input.putNull("parentId");
        } else {
            input.put("parentId", parent);
        }
        return create(admin, "/departments", input).path("id").asLong();
    }

    private JsonNode user(String admin, String username, long department, boolean manager) throws Exception {
        return create(admin, "/users", Map.of("username", username, "name", username,
                "departmentId", department, "departmentAdministrator", manager));
    }

    private String login(String username, String password) throws Exception {
        return readCreated(body(post(API + "/sessions"), Map.of("username", username, "password", password)))
                .path("accessToken").asText();
    }

    private JsonNode create(String token, String path, Object input) throws Exception {
        return readCreated(body(bearer(post(API + path), token), input));
    }

    private JsonNode readCreated(MockHttpServletRequestBuilder request) throws Exception {
        return json.readTree(mvc.perform(request).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsByteArray());
    }

    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object input) throws Exception {
        return request.contentType("application/json").content(json.writeValueAsBytes(input));
    }

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
