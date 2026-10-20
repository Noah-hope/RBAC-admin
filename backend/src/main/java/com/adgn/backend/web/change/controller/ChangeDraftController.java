package com.adgn.backend.web.change.controller;

import com.adgn.backend.change.ChangeDraftService;
import com.adgn.backend.web.IdempotencyHttp;
import com.adgn.backend.web.management.ManagementHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
@RequestMapping("/api/v1/admin")
public class ChangeDraftController {
    private final ChangeDraftService service;
    public ChangeDraftController(ChangeDraftService service){this.service=service;}
    @PostMapping("/roles") public ResponseEntity<Object> role(@AuthenticationPrincipal Jwt token,@RequestHeader("Idempotency-Key")String key,@RequestBody JsonNode input,HttpServletRequest request)throws Exception{
        return IdempotencyHttp.response(service.createRole(token,ManagementHeaders.context(request),key,input,correlation(request)),request);
    }
    @PostMapping("/change-requests") public ResponseEntity<Object> create(@AuthenticationPrincipal Jwt token,@RequestHeader("Idempotency-Key")String key,@RequestBody JsonNode input,HttpServletRequest request)throws Exception{
        return IdempotencyHttp.response(service.create(token,ManagementHeaders.context(request),key,input,correlation(request)),request);
    }
    @PutMapping("/change-requests/{requestId}") public ResponseEntity<Object> update(@AuthenticationPrincipal Jwt token,@RequestHeader("Idempotency-Key")String key,@PathVariable String requestId,@RequestBody JsonNode input,HttpServletRequest request)throws Exception{
        return IdempotencyHttp.response(service.update(token,ManagementHeaders.context(request),key,requestId,input,correlation(request)),request);
    }
    @DeleteMapping("/change-requests/{requestId}") public ResponseEntity<Object> delete(@AuthenticationPrincipal Jwt token,@RequestHeader("Idempotency-Key")String key,@PathVariable String requestId,@RequestParam String expectedDraftVersion,HttpServletRequest request)throws Exception{
        return IdempotencyHttp.response(service.delete(token,ManagementHeaders.context(request),key,requestId,expectedDraftVersion,correlation(request)),request);
    }
    private static String correlation(HttpServletRequest r){return (String)r.getAttribute("rbac.requestId");}
}
