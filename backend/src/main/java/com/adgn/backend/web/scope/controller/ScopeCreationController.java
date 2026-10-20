package com.adgn.backend.web.scope.controller;

import com.adgn.backend.scope.service.*;
import com.adgn.backend.query.preview.PreviewService;
import com.adgn.backend.web.IdempotencyHttp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
@RequestMapping("/api/v1/admin")
public class ScopeCreationController {
    private final ScopeCreationService service;
    public ScopeCreationController(ScopeCreationService service){this.service=service;}
    @PostMapping("/management-scope-previews")
    public ResponseEntity<PreviewService.View> preview(@AuthenticationPrincipal Jwt token,@RequestBody ScopeCreationInput input)throws Exception{
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.preview(token,input));
    }
    @PostMapping("/management-scopes")
    public ResponseEntity<Object> create(@AuthenticationPrincipal Jwt token,@RequestHeader("Idempotency-Key")String key,@RequestBody ScopeCreationService.Save input,HttpServletRequest request)throws Exception{
        return IdempotencyHttp.response(service.create(token,key,input,(String)request.getAttribute("rbac.requestId")),request);
    }
}
