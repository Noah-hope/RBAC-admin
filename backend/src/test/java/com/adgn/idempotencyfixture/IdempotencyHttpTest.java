package com.adgn.idempotencyfixture;
import com.adgn.backend.core.idempotency.*;
import com.adgn.backend.web.*;
import com.adgn.backend.web.problem.*;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
class IdempotencyHttpTest {
    private MockMvc mvc;
    @BeforeEach void setup(){var problems=new ProblemResponses(JsonMapper.builder().build());
        mvc=MockMvcBuilders.standaloneSetup(new Probe()).setControllerAdvice(new ApiExceptionHandler(problems)).addFilters(new RequestIdFilter(problems)).build();}
    @Test void conflictsUseDistinctCodesAndOnlyInProgressIncludesRetryAfter() throws Exception {
        var conflict=mvc.perform(get("/__idempotency/conflict")).andReturn().getResponse();
        assertThat(conflict.getStatus()).isEqualTo(409);assertThat(conflict.getContentAsString()).contains("IDEMPOTENCY_CONFLICT");assertThat(conflict.getHeader("Retry-After")).isNull();
        var busy=mvc.perform(get("/__idempotency/busy")).andReturn().getResponse();
        assertThat(busy.getStatus()).isEqualTo(409);assertThat(busy.getContentAsString()).contains("REQUEST_IN_PROGRESS");assertThat(busy.getHeader("Retry-After")).isEqualTo("7");
    }
    @Test void replaysOriginalJsonStatusAndLocationWithFreshRequestIdAndNoSecretHeaders() throws Exception {
        var result=mvc.perform(get("/__idempotency/replay").header("X-Request-Id","current-id")).andReturn().getResponse();
        assertThat(result.getStatus()).isEqualTo(201);assertThat(result.getContentAsString()).contains("value");assertThat(result.getHeader("Location")).isEqualTo("/api/v1/fixture/id");
        assertThat(result.getHeader("Idempotent-Replayed")).isEqualTo("true");assertThat(result.getHeader("X-Request-Id")).isEqualTo("current-id");assertThat(result.getHeader("Cache-Control")).isEqualTo("no-store");assertThat(result.getHeader("Set-Cookie")).isNull();
    }
    @Test void replayOf204HasNoBodyOrContentType() throws Exception {
        var result=mvc.perform(get("/__idempotency/empty")).andReturn().getResponse();
        assertThat(result.getStatus()).isEqualTo(204);assertThat(result.getContentAsString()).isEmpty();assertThat(result.getContentType()).isNull();assertThat(result.getHeader("Idempotent-Replayed")).isEqualTo("true");
    }
    @Test void cachedProblemGetsCurrentRequestIdInsteadOfOriginalRequestId() throws Exception {
        var result=mvc.perform(get("/__idempotency/problem").header("X-Request-Id","new-id")).andReturn().getResponse();
        assertThat(result.getStatus()).isEqualTo(409);assertThat(result.getContentType()).startsWith("application/problem+json");
        assertThat(result.getContentAsString()).contains("new-id","VERSION_CONFLICT").doesNotContain("old-id");
    }
    @RestController static class Probe {
        @GetMapping("/__idempotency/conflict") Object conflict(){throw new IdempotencyFailure(IdempotencyFailure.Code.IDEMPOTENCY_CONFLICT,0);}
        @GetMapping("/__idempotency/busy") Object busy(){throw new IdempotencyFailure(IdempotencyFailure.Code.REQUEST_IN_PROGRESS,7);}
        @GetMapping("/__idempotency/replay") ResponseEntity<Object> replay(HttpServletRequest r){return IdempotencyHttp.response(new IdempotencyCoordinator.Outcome(new SafeResult(201,"{\"value\":1}","/api/v1/fixture/id"),true),r);}
        @GetMapping("/__idempotency/empty") ResponseEntity<Object> empty(HttpServletRequest r){return IdempotencyHttp.response(new IdempotencyCoordinator.Outcome(new SafeResult(204,null,null),true),r);}
        @GetMapping("/__idempotency/problem") ResponseEntity<Object> problem(HttpServletRequest r){return IdempotencyHttp.response(new IdempotencyCoordinator.Outcome(new SafeResult(409,"{\"code\":\"VERSION_CONFLICT\",\"requestId\":\"old-id\"}",null),true),r);}
    }
}
