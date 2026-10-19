package com.adgn.backend.web.catalog.controller;

import com.adgn.backend.catalog.repository.SystemRepository;
import com.adgn.backend.catalog.service.SystemService;
import com.adgn.backend.web.management.ManagementHeaders;
import com.adgn.backend.web.IdempotencyHttp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
@RequestMapping("/api/v1/admin/systems")
public class SystemController {
    public record Create(String code,String name,String description,String reason) { }
    private final SystemService service;
    public SystemController(SystemService service){this.service=service;}
    @GetMapping public ResponseEntity<SystemRepository.Page> list(@AuthenticationPrincipal Jwt token,HttpServletRequest request,
            @RequestParam(required=false)String page,@RequestParam(required=false)String pageSize,@RequestParam(required=false)String enabled,@RequestParam(required=false)String q)throws Exception{
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(token,ManagementHeaders.context(request),page,pageSize,enabled,q));
    }
    @GetMapping("/{systemId}") public ResponseEntity<SystemRepository.View> get(@AuthenticationPrincipal Jwt token,@PathVariable String systemId,HttpServletRequest request)throws Exception{
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(token,ManagementHeaders.context(request),systemId));
    }
    @PostMapping public ResponseEntity<Object> create(@AuthenticationPrincipal Jwt token,@RequestHeader("Idempotency-Key")String key,@RequestBody Create input,HttpServletRequest request)throws Exception{
        return IdempotencyHttp.response(service.create(token,key,input.code(),input.name(),input.description(),input.reason(),(String)request.getAttribute("rbac.requestId")),request);
    }
}
