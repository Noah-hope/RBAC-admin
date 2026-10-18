package com.adgn.backend.web;
import com.adgn.backend.core.idempotency.IdempotencyCoordinator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;
public final class IdempotencyHttp {
    private IdempotencyHttp() { }
    private static final JsonMapper JSON=JsonMapper.builder().build();
    public static ResponseEntity<Object> response(IdempotencyCoordinator.Outcome outcome,HttpServletRequest request) {
        var result=outcome.result();var headers=new HttpHeaders();headers.setCacheControl("no-store");
        if(outcome.replayed())headers.set("Idempotent-Replayed","true");
        if(result.location()!=null)headers.set(HttpHeaders.LOCATION,result.location());
        Object body=null;
        if(result.body()!=null) {
            var node=JSON.readTree(result.body());
            boolean problem=result.status()>=400;
            headers.setContentType(problem?MediaType.APPLICATION_PROBLEM_JSON:MediaType.APPLICATION_JSON);
            if(problem && node.isObject()) ((tools.jackson.databind.node.ObjectNode)node).put("requestId",(String)request.getAttribute("rbac.requestId"));
            body=node;
        }
        return new ResponseEntity<>(body,headers,result.status());
    }
}
