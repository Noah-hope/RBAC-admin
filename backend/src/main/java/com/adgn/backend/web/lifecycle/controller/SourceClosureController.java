package com.adgn.backend.web.lifecycle.controller;

import com.adgn.backend.lifecycle.SourceClosureService;
import com.adgn.backend.web.IdempotencyHttp;
import com.adgn.backend.web.management.ManagementHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
@RequestMapping("/api/v1/admin")
public class SourceClosureController {
    private final SourceClosureService service;
    public SourceClosureController(SourceClosureService service){this.service=service;}
    @PostMapping("/source-closure-previews") public ResponseEntity<?> preview(@AuthenticationPrincipal Jwt token,@RequestBody JsonNode input,HttpServletRequest request)throws Exception{
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.preview(token,ManagementHeaders.context(request),input));
    }
    @PostMapping("/source-closures") public ResponseEntity<Object> close(@AuthenticationPrincipal Jwt token,@RequestHeader("Idempotency-Key")String key,@RequestBody JsonNode input,HttpServletRequest request)throws Exception{
        return IdempotencyHttp.response(service.close(token,ManagementHeaders.context(request),key,input,(String)request.getAttribute("rbac.requestId")),request);
    }
}
