package com.adgn.backend.web.scope.controller;

import com.adgn.backend.scope.service.ScopeQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
public class ScopeController {
    private final ScopeQueryService service;
    public ScopeController(ScopeQueryService service){this.service=service;}
    @GetMapping("/my-appointments")
    public ResponseEntity<?> mine(@AuthenticationPrincipal Jwt token,@RequestParam(required=false)String page,@RequestParam(required=false)String pageSize)throws Exception{return ok(service.mine(token,page,pageSize));}
    @GetMapping("/management-scopes")
    public ResponseEntity<?> list(@AuthenticationPrincipal Jwt token,@RequestParam(required=false)String page,@RequestParam(required=false)String pageSize,@RequestParam(required=false)String companyId,@RequestParam(required=false)String departmentId)throws Exception{return ok(service.list(token,page,pageSize,companyId,departmentId));}
    @GetMapping("/management-scopes/{scopeId}")
    public ResponseEntity<?> get(@AuthenticationPrincipal Jwt token,@PathVariable String scopeId)throws Exception{return ok(service.get(token,scopeId));}
    @GetMapping("/management-scopes/{scopeId}/appointments")
    public ResponseEntity<?> history(@AuthenticationPrincipal Jwt token,@PathVariable String scopeId,@RequestParam(required=false)String page,@RequestParam(required=false)String pageSize,@RequestParam(required=false)String status)throws Exception{return ok(service.history(token,scopeId,page,pageSize,status));}
    private static ResponseEntity<?> ok(Object body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
