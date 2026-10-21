package com.adgn.backend.web.lifecycle.controller;

import com.adgn.backend.lifecycle.*;
import com.adgn.backend.web.IdempotencyHttp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
@RequestMapping("/api/v1/admin/management-scopes/{scopeId}")
public class ScopeReplacementController {
    private final LifecycleService service;
    public ScopeReplacementController(LifecycleService service){this.service=service;}
    @PostMapping("/previews")
    public ResponseEntity<?> preview(@AuthenticationPrincipal Jwt token,@PathVariable String scopeId,@RequestBody LifecycleInput.ScopeEdit input)throws Exception{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.scopePreview(token,scopeId,input));}
    @PutMapping
    public ResponseEntity<Object> save(@AuthenticationPrincipal Jwt token,@PathVariable String scopeId,@RequestHeader("Idempotency-Key")String key,@RequestBody LifecycleInput.ScopeSave input,HttpServletRequest request)throws Exception{return IdempotencyHttp.response(service.replaceScope(token,scopeId,key,input,(String)request.getAttribute("rbac.requestId")),request);}
}
