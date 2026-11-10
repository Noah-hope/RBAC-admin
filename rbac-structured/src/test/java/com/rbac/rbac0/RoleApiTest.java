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
class RoleApiTest {

    private static final String API = "/api/v1";
    private static final String ROLES = API + "/roles";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate database;

    @Test
    void createsRoleAcrossApplicationsAndRejectsIncompleteOrInvalidSelection() throws Exception {
        var token = admin();
        String suffix = unique();
        long firstApp = create(token, API + "/applications", Map.of("name", "公文-" + suffix)).path("id").asLong();
        long secondApp = create(token, API + "/applications", Map.of("name", "门户-" + suffix)).path("id").asLong();
        long firstMenu = menu(token, firstApp);
        long secondMenu = menu(token, secondApp);
        long button = create(token, permissions(firstApp),
                Map.of("name", "发布", "code", "document:publish", "type", "BUTTON", "parentId", firstMenu))
                .path("id").asLong();

        for (var ids : List.of(List.of(button), List.of(firstMenu, firstMenu), List.of(9_007_199_254_740_991L))) {
            mvc.perform(body(bearer(post(ROLES), token), Map.of("name", "无效角色", "permissionIds", ids)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        String roleName = "发布员-" + suffix;
        long role = create(token, ROLES,
                Map.of("name", roleName, "description", "发布公文", "permissionIds", List.of(button, secondMenu, firstMenu)))
                .path("id").asLong();

        mvc.perform(bearer(get(ROLES + "/" + role), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("发布公文"))
                .andExpect(jsonPath("$.permissionIds.length()").value(3))
                .andExpect(jsonPath("$.applications[0].applicationId").value(firstApp))
                .andExpect(jsonPath("$.applications[0].permissionCount").value(2))
                .andExpect(jsonPath("$.applications[1].applicationId").value(secondApp))
                .andExpect(jsonPath("$.applications[1].permissionCount").value(1));
        mvc.perform(bearer(get(ROLES).param("keyword", suffix).param("pageSize", "1"), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(role))
                .andExpect(jsonPath("$.items[0].applications.length()").value(2))
                .andExpect(jsonPath("$.items[0].permissionIds").doesNotExist());
    }

    @Test
    void replacesAllGrantsAndRollsBackChangesAndLogOnConflict() throws Exception {
        var token = admin();
        String suffix = unique();
        long app = create(token, API + "/applications", Map.of("name", "授权-" + suffix)).path("id").asLong();
        long previous = menu(token, app);
        long next = create(token, permissions(app), Map.of("name", "档案", "code", "archive:view", "type", "MENU"))
                .path("id").asLong();
        String originalName = "原角色-" + suffix;
        long role = create(token, ROLES, Map.of("name", originalName, "permissionIds", List.of(previous)))
                .path("id").asLong();
        String taken = "已用-" + suffix;
        create(token, ROLES, Map.of("name", taken, "permissionIds", List.of()));

        mvc.perform(body(bearer(put(ROLES + "/" + role), token), Map.of("name", taken, "permissionIds", List.of(next))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(bearer(get(ROLES + "/" + role), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value(originalName))
                .andExpect(jsonPath("$.permissionIds[0]").value(previous));
        assertThat(updateLogs(role)).isZero();
        mvc.perform(body(bearer(put(ROLES + "/" + role), token),
                        Map.of("name", originalName, "description", "新说明", "permissionIds", List.of(next))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permissionIds.length()").value(1))
                .andExpect(jsonPath("$.permissionIds[0]").value(next));
        assertThat(updateLogs(role)).isEqualTo(1L);
        mvc.perform(body(bearer(put(ROLES + "/" + role), token), Map.of("name", originalName, "permissionIds", List.of())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value(""))
                .andExpect(jsonPath("$.permissionIds").isEmpty()).andExpect(jsonPath("$.applications").isEmpty());
    }

    @Test
    void departmentAdministratorCanReadOptionsButCannotConfigureRoles() throws Exception {
        var token = admin();
        String suffix = unique();
        long role = create(token, ROLES, Map.of("name", "选项-" + suffix, "permissionIds", List.of())).path("id").asLong();
        long department = create(token, API + "/departments",
                json.createObjectNode().put("name", "角色测试部").putNull("parentId")).path("id").asLong();
        String username = "role-manager-" + suffix;
        var manager = create(token, API + "/users", Map.of("username", username, "name", "部门管理员",
                "departmentId", department, "departmentAdministrator", true));
        var managerToken = login(username, manager.path("initialPassword").asText());

        mvc.perform(bearer(get(ROLES).param("keyword", suffix), managerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(role));
        mvc.perform(bearer(get(ROLES + "/" + role), managerToken)).andExpect(status().isForbidden());
        mvc.perform(body(bearer(post(ROLES), managerToken), Map.of("name", "越权", "permissionIds", List.of())))
                .andExpect(status().isForbidden());
        mvc.perform(body(bearer(put(ROLES + "/" + role), managerToken), Map.of("name", "越权", "permissionIds", List.of())))
                .andExpect(status().isForbidden());

        long systemRole = database.queryForObject("SELECT id FROM sys_role WHERE role_type = 'DEPARTMENT_ADMIN'", Long.class);
        mvc.perform(bearer(get(ROLES + "/" + systemRole), token)).andExpect(status().isNotFound());
        mvc.perform(bearer(get(ROLES).param("keyword", "部门管理员"), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    private long updateLogs(long role) {
        return database.queryForObject("""
                SELECT COUNT(*) FROM sys_operation_log log
                JOIN sys_operation_log_target target ON target.operation_log_id = log.id
                WHERE log.action = '修改角色' AND log.scope = 'GLOBAL' AND target.target_type = 'ROLE'
                  AND target.target_id = ?
                """, Long.class, role);
    }

    private long menu(String token, long app) throws Exception {
        return create(token, permissions(app), Map.of("name", "公文", "code", "document:view", "type", "MENU"))
                .path("id").asLong();
    }

    private static String permissions(long app) {
        return API + "/applications/" + app + "/permissions";
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

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
