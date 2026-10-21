package com.adgn.backend.web.audit.controller;

import com.adgn.backend.query.audit.AuditQueryService;
import com.adgn.backend.web.management.ManagementHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
@RequestMapping("/api/v1/admin/audit-events")
public class AuditQueryController {
    private final AuditQueryService service;
    public AuditQueryController(AuditQueryService service){this.service=service;}
    @GetMapping public ResponseEntity<?> list(@AuthenticationPrincipal Jwt token,HttpServletRequest request)throws Exception{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(token,ManagementHeaders.context(request),QueryParameters.from(request)));}
    @GetMapping("/{auditEventId}") public ResponseEntity<?> get(@AuthenticationPrincipal Jwt token,@PathVariable String auditEventId,HttpServletRequest request)throws Exception{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(token,ManagementHeaders.context(request),auditEventId));}
}
