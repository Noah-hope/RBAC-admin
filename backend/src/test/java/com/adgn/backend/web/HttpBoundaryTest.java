package com.adgn.backend.web;

import com.adgn.backend.core.transaction.PersistenceFailure;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Servlet HTTP integration; probe endpoints exist only in the test ApplicationContext. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(HttpBoundaryTest.ProbeController.class)
@ExtendWith(OutputCaptureExtension.class)
class HttpBoundaryTest {
    private static final String HEADER = "X-Request-Id";
    @Autowired MockMvc mvc;
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void disabledIdentityFailsClosedForEveryProtectedFamily() throws Exception {
        for (String path : List.of("/api/v1/admin/accounts", "/api/v1/integration/identity-probe", "/api/v1/oa/identity-probe")) {
            var response = send("GET", path, null, null);
            assertProblem(response, 503, "DEPENDENCY_UNAVAILABLE");
        }
    }

    @Test
    void encodedRouteStillUsesTheContainersDecodedProtectedServletPath() throws Exception {
        var response=mvc.perform(MockMvcRequestBuilders.get(java.net.URI.create("/api/v1/%61dmin/accounts"))
                .with(request->{request.setServletPath("/api/v1/admin/accounts");return request;}))
                .andReturn().getResponse();
        assertProblem(response,503,"DEPENDENCY_UNAVAILABLE");
    }

    @Test
    void carriesClientRequestIdInResponseAndLoggingContext(CapturedOutput output) throws Exception {
        var response = send("POST", "/__test/echo", "{\"name\":\"测试\"}", "req_ABC-1");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HEADER)).isEqualTo("req_ABC-1");
        assertThat(json.readTree(response.getContentAsString()).path("requestId").stringValue()).isEqualTo("req_ABC-1");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(output.getAll()).contains("req_ABC-1", "http_request", "status=200");
    }

    @Test
    void generatesDifferentSafeRequestIdsWithoutLeakingPreviousContext() throws Exception {
        send("POST", "/__test/echo", "{\"name\":\"first\"}", "previous-request");
        var first = send("POST", "/__test/echo", "{\"name\":\"next\"}", null);
        var second = send("POST", "/__test/echo", "{\"name\":\"next\"}", null);
        String firstId = first.getHeader(HEADER);
        assertThat(firstId).matches("[A-Za-z0-9_-]{1,64}").isNotEqualTo("previous-request");
        assertThat(json.readTree(first.getContentAsString()).path("requestId").stringValue()).isEqualTo(firstId);
        assertThat(second.getHeader(HEADER))
                .matches("[A-Za-z0-9_-]{1,64}").isNotEqualTo(firstId);
    }

    @Test
    void acceptsMaximumLengthRequestId() throws Exception {
        var response = send("GET", "/actuator/health", null, "a".repeat(64));
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader(HEADER)).isEqualTo("a".repeat(64));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "unsafe/id", "unsafe id", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void rejectsInvalidRequestIdsWithSafeReplacement(String id) throws Exception {
        var response = send("POST", "/__test/echo", "{\"name\":\"ok\"}", id);
        assertProblem(response, 422, "VALIDATION_ERROR");
        assertThat(response.getHeader(HEADER)).matches("[A-Za-z0-9_-]{1,64}");
        assertThat(json.readTree(response.getContentAsString()).path("errors").get(0).path("pointer").stringValue())
                .isEqualTo("/headers/X-Request-Id");
    }

    @Test
    void rejectsAmbiguousRepeatedRequestIdHeaders() throws Exception {
        var response = mvc.perform(MockMvcRequestBuilders.get("/actuator/health")
                .header(HEADER, "first", "second")).andReturn().getResponse();
        assertProblem(response, 422, "VALIDATION_ERROR");
    }

    @Test
    void rejectsUnknownJsonFieldsAndEscapesJsonPointer(CapturedOutput output) throws Exception {
        var response = send("POST", "/__test/echo", "{\"name\":\"ok\",\"a~/b\":\"do-not-log-this-value\"}", "unknown-field");
        JsonNode problem = assertProblem(response, 422, "VALIDATION_ERROR");
        assertThat(problem.path("errors").get(0).path("pointer").stringValue()).isEqualTo("/a~0~1b");
        assertThat(response.getContentAsString()).doesNotContain("do-not-log-this-value");
        assertThat(output.getAll()).doesNotContain("do-not-log-this-value");
    }

    @Test
    void validatesFieldsWithoutEchoingRejectedValues() throws Exception {
        var response = send("POST", "/__test/echo", "{\"name\":\" \"}", "invalid-field");
        var problem = assertProblem(response, 422, "VALIDATION_ERROR");
        assertThat(problem.path("errors").get(0).path("pointer").stringValue()).isEqualTo("/name");
    }

    @Test
    void reportsNestedArrayValidationAsJsonPointer() throws Exception {
        var response = send("POST", "/__test/batch", "{\"entries\":[{\"name\":\"\"}]}", "nested-field");
        var problem = assertProblem(response, 422, "VALIDATION_ERROR");
        assertThat(problem.path("errors").get(0).path("pointer").stringValue()).isEqualTo("/entries/0/name");
    }

    @Test
    void rejectsMalformedJsonWith400() throws Exception {
        assertProblem(send("POST", "/__test/echo", "{", "bad-json"), 400, "MALFORMED_REQUEST");
    }

    @Test
    void rejectsMissingRequestBodyWith400() throws Exception {
        assertProblem(send("POST", "/__test/echo", "", "missing-body"), 400, "MALFORMED_REQUEST");
    }

    @Test
    void reportsMissingRequiredHeaderAs422() throws Exception {
        var response = send("GET", "/__test/context", null, "missing-context");
        var problem = assertProblem(response, 422, "VALIDATION_ERROR");
        assertThat(problem.path("errors").get(0).path("pointer").stringValue())
                .isEqualTo("/headers/X-Admin-Scope-Version");
    }

    @Test
    void reportsMissingRequiredQueryAs422() throws Exception {
        var problem = assertProblem(send("GET", "/__test/page", null, "missing-query"), 422, "VALIDATION_ERROR");
        assertThat(problem.path("errors").get(0).path("pointer").stringValue()).isEqualTo("/query/page");
    }

    @Test
    void validatesQueryBoundsAs422() throws Exception {
        assertProblem(send("GET", "/__test/page?page=0", null, "invalid-query"), 422, "VALIDATION_ERROR");
    }

    @Test
    void reportsQuerySyntaxErrorsAs400() throws Exception {
        assertProblem(send("GET", "/__test/page?page=oops", null, "malformed-query"), 400, "MALFORMED_REQUEST");
    }

    @Test
    void rejectsScalarCoercionRatherThanSilentlyConvertingNumbersToStrings() throws Exception {
        assertProblem(send("POST", "/__test/echo", "{\"name\":123}", "bad-type"), 422, "VALIDATION_ERROR");
    }

    @Test
    void rejectsTrailingJsonInsteadOfIgnoringSecondDocument() throws Exception {
        assertProblem(send("POST", "/__test/echo", "{\"name\":\"ok\"} {}", "trailing-json"), 400, "MALFORMED_REQUEST");
    }

    @Test
    void rejectsDuplicateJsonKeysRatherThanTakingLastValue() throws Exception {
        assertProblem(send("POST", "/__test/echo", "{\"name\":\"first\",\"name\":\"last\"}", "duplicate-json"),
                400, "MALFORMED_REQUEST");
    }

    @Test
    void usesProblemForUnknownRoutesWithoutEchoingQuery() throws Exception {
        var response = send("GET", "/api/v1/does-not-exist?token=never-echo-this", null, "missing-route");
        assertProblem(response, 404, "NOT_FOUND");
        assertThat(response.getContentAsString()).doesNotContain("never-echo-this");
    }

    @Test
    void preservesMethodNotAllowedStatusAndAllowHeader() throws Exception {
        var response = send("PUT", "/__test/echo", "{}", "wrong-method");
        assertProblem(response, 405, "MALFORMED_REQUEST");
        assertThat(response.getHeader("Allow")).contains("POST");
    }

    @Test
    void rejectsUnsupportedMediaTypeWithProblem() throws Exception {
        var response = mvc.perform(MockMvcRequestBuilders.post("/__test/echo")
                .contentType("text/plain").header(HEADER, "wrong-media").content("text"))
                .andReturn().getResponse();
        assertProblem(response, 415, "UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void mapsConflictWithoutLeakingFrameworkReason() throws Exception {
        var response = send("GET", "/__test/conflict", null, "state-conflict");
        assertProblem(response, 409, "STATE_CONFLICT");
        assertThat(response.getContentAsString()).doesNotContain("private-conflict-detail");
    }

    @Test
    void unexpectedFailureReturnsSafeProblemAndNoExceptionContents(CapturedOutput output) throws Exception {
        var response = send("GET", "/__test/failure", null, "failed-request");
        assertProblem(response, 500, "DATA_INTEGRITY_ERROR");
        assertThat(response.getContentAsString()).doesNotContain("SELECT", "private-password", "stackTrace", "IllegalStateException");
        assertThat(output.getAll()).contains("failed-request").doesNotContain("private-password");
    }

    @Test
    void storageFailureReturns503WithoutLeakingDatabaseDetails(CapturedOutput output) throws Exception {
        var response = send("GET", "/__test/storage-failure", null, "storage-request");
        assertProblem(response, 503, "DEPENDENCY_UNAVAILABLE");
        assertThat(response.getContentAsString()).doesNotContain("private-storage-fixture", "SQLException", "SELECT");
        assertThat(output.getAll()).doesNotContain("private-storage-fixture");
    }

    @Test
    void exposesHealthButNoDetailedEnvironment() throws Exception {
        var health = send("GET", "/actuator/health", null, "health-check");
        assertThat(health.getStatus()).isEqualTo(200);
        assertThat(json.readTree(health.getContentAsString()).path("status").stringValue()).isEqualTo("UP");
        assertThat(health.getContentAsString()).doesNotContain("components", "details");
        assertProblem(send("GET", "/actuator/env", null, "no-env"), 404, "NOT_FOUND");
    }

    private JsonNode assertProblem(MockHttpServletResponse response, int status, String code) throws Exception {
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getHeader("Content-Type")).startsWith("application/problem+json");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        JsonNode body = json.readTree(response.getContentAsString());
        assertThat(body.path("status").intValue()).isEqualTo(status);
        assertThat(body.path("code").stringValue()).isEqualTo(code);
        assertThat(body.path("type").stringValue()).isEqualTo("urn:rbac-admin:problem:" + code.toLowerCase().replace('_', '-'));
        assertThat(body.path("title").stringValue()).isNotBlank().hasSizeLessThanOrEqualTo(120);
        assertThat(body.path("detail").stringValue()).isNotBlank().hasSizeLessThanOrEqualTo(500);
        assertThat(body.path("requestId").stringValue()).isEqualTo(response.getHeader(HEADER));
        assertThat(body.propertyNames()).allMatch(List.of("type", "title", "status", "code", "detail", "requestId", "errors")::contains);
        return body;
    }

    private MockHttpServletResponse send(String method, String path, String body, String id) throws Exception {
        var builder = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path)
                .contentType("application/json");
        if (id != null) builder.header(HEADER, id);
        if (body != null) builder.content(body);
        return mvc.perform(builder).andReturn().getResponse();
    }

    @RestController
    static class ProbeController {
        @PostMapping("/__test/echo")
        EchoResult echo(@Valid @RequestBody EchoInput input) { return new EchoResult(input.name(), MDC.get("requestId")); }
        @PostMapping("/__test/batch")
        Map<String, Integer> batch(@Valid @RequestBody BatchInput input) { return Map.of("count", input.entries().size()); }
        @GetMapping("/__test/conflict")
        void conflict() { throw new ResponseStatusException(HttpStatus.CONFLICT, "private-conflict-detail"); }
        @GetMapping("/__test/failure")
        void failure() { throw new IllegalStateException("SELECT private-password FROM secrets"); }
        @GetMapping("/__test/storage-failure")
        void storageFailure() {
            throw new PersistenceFailure(PersistenceFailure.Kind.DEPENDENCY_UNAVAILABLE,
                    new java.sql.SQLException("SELECT private-storage-fixture"));
        }
        @GetMapping("/__test/context")
        Map<String, Long> context(@RequestHeader("X-Admin-Scope-Version") long version) { return Map.of("version", version); }
        @GetMapping("/__test/page")
        Map<String, Integer> page(@RequestParam @Min(1) Integer page) { return Map.of("page", page); }
    }

    record EchoInput(@NotBlank String name) { }
    record EchoResult(String name, String requestId) { }
    record BatchInput(@Valid List<EchoInput> entries) { }
}
