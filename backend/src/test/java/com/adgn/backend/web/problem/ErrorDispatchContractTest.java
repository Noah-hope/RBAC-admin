package com.adgn.backend.web.problem;

import com.adgn.backend.core.idempotency.IdempotencyFailure;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class ErrorDispatchContractTest {
    private final ProblemResponses problems=new ProblemResponses(JsonMapper.builder().build());

    @Test void containerDispatchBoundsStatusAndNeverReflectsItsPrivateException() {
        Object[] inputs={null,"500",399,600,404,500,599};
        int[] statuses={404,404,404,404,404,500,599};
        for(int i=0;i<inputs.length;i++){
            var request=new MockHttpServletRequest();request.setAttribute("rbac.requestId","dispatch-id");
            request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE,inputs[i]);
            request.setAttribute(RequestDispatcher.ERROR_MESSAGE,"private-sql-fixture");
            var response=new ProblemErrorController(problems).error(request);
            assertThat(response.getStatusCode().value()).isEqualTo(statuses[i]);
            var body=(ApiProblem)response.getBody();assertThat(body.status()).isEqualTo(statuses[i]);
            assertThat(body.requestId()).isEqualTo("dispatch-id");
            assertThat(body.toString()).doesNotContain("private-sql-fixture");
            assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        }
    }

    @Test void idempotencyAuthenticationAndAuthorizationFailuresKeepTheirOwnHttpStatuses() {
        var handler=new ApiExceptionHandler(problems);var request=new MockHttpServletRequest();
        var unauthenticated=handler.idempotencyFailure(new IdempotencyFailure(IdempotencyFailure.Code.AUTHENTICATION_FAILED,0),request);
        assertThat(unauthenticated.getStatusCode().value()).isEqualTo(401);
        assertThat(((ApiProblem)unauthenticated.getBody()).code()).isEqualTo("AUTHENTICATION_FAILED");
        var forbidden=handler.idempotencyFailure(new IdempotencyFailure(IdempotencyFailure.Code.FORBIDDEN,0),request);
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);
        assertThat(((ApiProblem)forbidden.getBody()).code()).isEqualTo("FORBIDDEN");
    }
}
