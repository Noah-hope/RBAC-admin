package com.adgn.backend.web.authorization.controller;

import com.adgn.backend.query.authorization.AuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
@RequestMapping("/api/v1/integration")
public class AuthorizationController {
    private final AuthorizationService service;
    public AuthorizationController(AuthorizationService service){this.service=service;}
    @PostMapping("/authorization-queries") public ResponseEntity<AuthorizationService.Result> query(@AuthenticationPrincipal Jwt token,@RequestBody JsonNode input,HttpServletRequest request)throws Exception{
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.query(token,input,(String)request.getAttribute("rbac.requestId")));
    }
    @PostMapping("/permission-checks") public ResponseEntity<AuthorizationService.Check> check(@AuthenticationPrincipal Jwt token,@RequestBody JsonNode input,HttpServletRequest request)throws Exception{
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.check(token,input,(String)request.getAttribute("rbac.requestId")));
    }
}
