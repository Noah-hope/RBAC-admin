package com.rbac.rbac0;

import java.util.Map;
import java.util.UUID;

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
class DepartmentApiTest {

    private static final String DEPARTMENTS = "/api/v1/departments";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate database;

    @Test
    void totalAdministratorMaintainsTheTreeAndCannotCreateACycle() throws Exception {
        var token = login("integration-admin");
        long root = create(token, "总部", null);
        long branch = create(token, "分部", root);
        long team = create(token, "档案组", branch);

        mvc.perform(bearer(get(DEPARTMENTS), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departments[?(@.id == " + root + ")].children[0].children[0].id")
                        .value(org.hamcrest.Matchers.contains((int) team)));
        mvc.perform(body(bearer(patch(DEPARTMENTS + "/" + root), token), Map.of("parentId", team)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(body(bearer(patch(DEPARTMENTS + "/" + team), token), Map.of()))
                .andExpect(status().isBadRequest());
        mvc.perform(body(bearer(post(DEPARTMENTS), token), Map.of("name", "缺少上级字段")))
                .andExpect(status().isBadRequest());
        mvc.perform(body(bearer(post(DEPARTMENTS), token), Map.of("name", "未知上级", "parentId", 9_007_199_254_740_991L)))
                .andExpect(status().isNotFound());
        mvc.perform(body(bearer(post(DEPARTMENTS), token), Map.of("name", "字符串上级", "parentId", Long.toString(root))))
                .andExpect(status().isBadRequest());

        var move = json.createObjectNode().put("name", "档案室").putNull("parentId");
        mvc.perform(body(bearer(patch(DEPARTMENTS + "/" + team), token), move))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("档案室"))
                .andExpect(jsonPath("$.parentId").value(org.hamcrest.Matchers.nullValue()));
        assertThat(database.queryForObject("""
                SELECT log.detail FROM sys_operation_log log
                JOIN sys_operation_log_target target ON target.operation_log_id = log.id
                WHERE log.scope = 'GLOBAL' AND log.action = '修改部门' AND target.target_id = ?
                """, String.class, team)).contains("档案组", "档案室", "顶层");
    }

    @Test
    void departmentAdministratorOnlySeesTheirOwnSubtree() throws Exception {
        var totalAdmin = login("integration-admin");
        long root = create(totalAdmin, "总公司", null);
        long department = create(totalAdmin, "人事部", root);
        long child = create(totalAdmin, "档案组", department);
        create(totalAdmin, "财务部", root);
        String manager = "dept-" + UUID.randomUUID().toString().substring(0, 8);
        database.update("""
                INSERT INTO sys_user (department_id, username, name, password_hash, status)
                SELECT ?, ?, '部门管理员测试', password_hash, 'ACTIVE' FROM sys_user WHERE username = 'integration-admin'
                """, department, manager);
        database.update("""
                INSERT INTO sys_user_role (user_id, role_id)
                SELECT u.id, r.id FROM sys_user u, sys_role r
                WHERE u.username = ? AND r.role_type = 'DEPARTMENT_ADMIN'
                """, manager);
        var managerToken = login(manager);

        mvc.perform(bearer(get(DEPARTMENTS), managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departments.length()").value(1))
                .andExpect(jsonPath("$.departments[0].id").value(department))
                .andExpect(jsonPath("$.departments[0].children.length()").value(1))
                .andExpect(jsonPath("$.departments[0].children[0].id").value(child));
        mvc.perform(body(bearer(post(DEPARTMENTS), managerToken), Map.of("name", "临时组", "parentId", department)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get(DEPARTMENTS)).andExpect(status().isUnauthorized());
    }

    private long create(String token, String name, Long parent) throws Exception {
        var input = json.createObjectNode().put("name", name);
        if (parent == null) {
            input.putNull("parentId");
        } else {
            input.put("parentId", parent);
        }
        var response = mvc.perform(body(bearer(post(DEPARTMENTS), token), input))
                .andExpect(status().isCreated()).andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray()).path("id").asLong();
    }

    private String login(String username) throws Exception {
        var response = mvc.perform(body(post("/api/v1/sessions"),
                        Map.of("username", username, "password", "integration-test-password")))
                .andExpect(status().isCreated()).andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray()).path("accessToken").asText();
    }

    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object value) throws Exception {
        return request.contentType("application/json").content(json.writeValueAsBytes(value));
    }

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }
}
