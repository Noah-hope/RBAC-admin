package com.rbac.rbac0;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=${RBAC_TEST_DB_URL}",
        "spring.datasource.username=${RBAC_TEST_DB_USERNAME}",
        "spring.datasource.password=${RBAC_TEST_DB_PASSWORD:}",
        "rbac.bootstrap.username=integration-admin",
        "rbac.bootstrap.password=integration-test-password"
})
@AutoConfigureMockMvc
class SessionApiTest {

    private static final String SESSIONS = "/api/v1/sessions";
    private static final String ME = "/api/v1/me";
    private static final String ADMIN = """
            {"username":"integration-admin","password":"integration-test-password"}
            """;
    private static final String WRONG_PASSWORD = """
            {"username":"integration-admin","password":"wrong-password"}
            """;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate database;

    @Test
    void totalAdministratorCanSignInAndRevokeOnlyTheCurrentSession() throws Exception {
        var first = login();
        var second = login();

        mvc.perform(get(ME).header("Authorization", "Bearer " + first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("integration-admin"))
                .andExpect(jsonPath("$.departmentId").isEmpty())
                .andExpect(jsonPath("$.managementRole").value("TOTAL_ADMIN"));

        mvc.perform(delete(SESSIONS + "/current").header("Authorization", "Bearer " + first))
                .andExpect(status().isNoContent());
        assertThat(database.queryForObject(
                "SELECT COUNT(*) FROM sys_session WHERE token_hash = UNHEX(SHA2(?, 256))",
                Long.class, first)).isZero();

        mvc.perform(get(ME).header("Authorization", "Bearer " + first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get(ME).header("Authorization", "Bearer " + second))
                .andExpect(status().isOk());
    }

    @Test
    void expiredSessionCannotIdentifyAUser() throws Exception {
        var token = login();
        assertThat(database.update("""
                UPDATE sys_session SET expires_at = '2000-01-01 00:00:00'
                WHERE token_hash = UNHEX(SHA2(?, 256))
                """, token)).isEqualTo(1);

        mvc.perform(get(ME).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAccountCannotLogInOrUseAnExistingSession() throws Exception {
        var token = login();
        database.update("UPDATE sys_user SET status = 'DISABLED' WHERE username = 'integration-admin'");
        try {
            mvc.perform(post(SESSIONS).contentType("application/json").content(ADMIN))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
            mvc.perform(get(ME).header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        } finally {
            database.update("UPDATE sys_user SET status = 'ACTIVE' WHERE username = 'integration-admin'");
        }
    }

    @Test
    void invalidCredentialsAreRateLimitedAfterRepeatedFailures() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post(SESSIONS).with(request -> {
                        request.setRemoteAddr("192.0.2.12");
                        return request;
                    }).contentType("application/json").content(WRONG_PASSWORD))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
        mvc.perform(post(SESSIONS).with(request -> {
                    request.setRemoteAddr("192.0.2.12");
                    return request;
                }).contentType("application/json").content(ADMIN))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void malformedLoginAndMissingBearerReturnContractErrors() throws Exception {
        mvc.perform(post(SESSIONS).contentType("application/json").content("{\"username\":\"admin\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post(SESSIONS).contentType("application/json")
                        .content("{\"username\":\"integration-admin\",\"password\":123456}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get(ME))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private String login() throws Exception {
        var response = mvc.perform(post(SESSIONS).contentType("application/json").content(ADMIN))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.expiresAt").isString())
                .andExpect(jsonPath("$.user.managementRole").value("TOTAL_ADMIN"))
                .andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray()).get("accessToken").asText();
    }
}
