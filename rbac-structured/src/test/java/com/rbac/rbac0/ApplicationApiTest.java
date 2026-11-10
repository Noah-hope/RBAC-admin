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
class ApplicationApiTest {

    private static final String API = "/api/v1";
    private static final String APPLICATIONS = API + "/applications";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate database;

    @Test
    void registersRenamesAndPaginatesApplications() throws Exception {
        var token = admin();
        String name = "工单-" + unique();
        long id = application(token, name);
        long nextId = application(token, name + "-二");

        mvc.perform(body(bearer(post(APPLICATIONS), token), Map.of("name", name)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(body(bearer(patch(APPLICATIONS + "/" + id), token), Map.of("name", name + "-二")))
                .andExpect(status().isConflict());
        mvc.perform(body(bearer(patch(APPLICATIONS + "/" + id), token), Map.of("name", name + "-改")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value(name + "-改"));

        int page = database.queryForObject("SELECT COUNT(*) FROM sys_application WHERE id <= ?", Integer.class, nextId);
        mvc.perform(bearer(get(APPLICATIONS).param("page", Integer.toString(page)).param("pageSize", "1"), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(page))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(nextId))
                .andExpect(jsonPath("$.items[0].tokenHash").doesNotExist());
        assertThat(logDetail(id, "APPLICATION", "修改应用")).contains(name, name + "-改");
    }

    @Test
    void buildsPermissionTreeAndEnforcesApplicationBoundaries() throws Exception {
        var token = admin();
        long app = application(token, "OA-" + unique());
        long other = application(token, "门户-" + unique());
        String permissions = APPLICATIONS + "/" + app + "/permissions";
        String otherPermissions = APPLICATIONS + "/" + other + "/permissions";
        long menu = create(token, permissions, Map.of("name", "公文管理", "code", "document:view", "type", "MENU"))
                .path("id").asLong();
        var input = Map.of("name", "审批", "code", "document:approve", "type", "BUTTON", "parentId", menu);
        long button = create(token, permissions, input).path("id").asLong();

        mvc.perform(body(bearer(post(permissions), token), input)).andExpect(status().isConflict());
        mvc.perform(body(bearer(post(otherPermissions), token), input)).andExpect(status().isBadRequest());
        mvc.perform(body(bearer(post(permissions), token),
                        Map.of("name", "子按钮", "code", "document:cancel", "type", "BUTTON", "parentId", button)))
                .andExpect(status().isBadRequest());
        mvc.perform(body(bearer(post(permissions), token),
                        Map.of("name", "错误标识", "code", "document:bad code", "type", "MENU")))
                .andExpect(status().isBadRequest());
        create(token, otherPermissions, Map.of("name", "另一应用", "code", "document:view", "type", "MENU"));

        mvc.perform(body(bearer(patch(otherPermissions + "/" + button), token), Map.of("name", "越界修改")))
                .andExpect(status().isNotFound());
        mvc.perform(body(bearer(patch(permissions + "/" + button), token), Map.of("type", "MENU")))
                .andExpect(status().isBadRequest());
        mvc.perform(body(bearer(patch(permissions + "/" + button), token), Map.of("code", "document:view")))
                .andExpect(status().isConflict());
        mvc.perform(body(bearer(patch(permissions + "/" + button), token), Map.of("name", "审批公文", "code", "document:review")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(button))
                .andExpect(jsonPath("$.parentId").value(menu)).andExpect(jsonPath("$.type").value("BUTTON"));
        mvc.perform(bearer(get(permissions), token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(app)).andExpect(jsonPath("$.menus.length()").value(1))
                .andExpect(jsonPath("$.menus[0].id").value(menu))
                .andExpect(jsonPath("$.menus[0].buttons[0].code").value("document:review"));
        assertThat(logDetail(button, "PERMISSION", "修改权限")).contains("document:approve", "document:review");
    }

    @Test
    void departmentAdministratorCannotManageApplicationPermissions() throws Exception {
        var total = admin();
        long app = application(total, "范围-" + unique());
        long department = create(total, API + "/departments", json.createObjectNode().put("name", "业务部").putNull("parentId"))
                .path("id").asLong();
        String username = "app-manager-" + unique();
        var manager = create(total, API + "/users", Map.of("username", username, "name", "部门管理员",
                "departmentId", department, "departmentAdministrator", true));
        var token = login(username, manager.path("initialPassword").asText());
        mvc.perform(get(APPLICATIONS)).andExpect(status().isUnauthorized());
        mvc.perform(bearer(get(APPLICATIONS), token)).andExpect(status().isForbidden());
        mvc.perform(body(bearer(post(APPLICATIONS), token), Map.of("name", "越权新增")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(bearer(get(APPLICATIONS + "/" + app + "/permissions"), token)).andExpect(status().isForbidden());
    }

    private String logDetail(long id, String type, String action) {
        return database.queryForObject("""
                SELECT log.detail FROM sys_operation_log log
                JOIN sys_operation_log_target target ON target.operation_log_id = log.id
                WHERE log.scope = 'GLOBAL' AND log.action = ? AND target.target_type = ? AND target.target_id = ?
                """, String.class, action, type, id);
    }

    private long application(String token, String name) throws Exception {
        return create(token, APPLICATIONS, Map.of("name", name)).path("id").asLong();
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
