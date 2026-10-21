package com.adgn.backend.web.change.controller;

import com.adgn.backend.query.change.ChangeQueryService;
import com.adgn.backend.query.audit.AuditQueryService;
import com.adgn.backend.web.audit.controller.QueryParameters;
import com.adgn.backend.web.management.ManagementHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
@RequestMapping("/api/v1/admin/change-requests")
public class ChangeQueryController {
    private final ChangeQueryService changes;private final AuditQueryService audit;
    public ChangeQueryController(ChangeQueryService changes,AuditQueryService audit){this.changes=changes;this.audit=audit;}
    @GetMapping public ResponseEntity<?> list(@AuthenticationPrincipal Jwt token,HttpServletRequest request)throws Exception{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(changes.list(token,ManagementHeaders.context(request),QueryParameters.from(request)));}
    @GetMapping("/{requestId}") public ResponseEntity<?> get(@AuthenticationPrincipal Jwt token,@PathVariable String requestId,HttpServletRequest request)throws Exception{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(changes.get(token,ManagementHeaders.context(request),requestId));}
    @GetMapping("/{requestId}/events") public ResponseEntity<?> events(@AuthenticationPrincipal Jwt token,@PathVariable String requestId,HttpServletRequest request)throws Exception{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(audit.events(token,ManagementHeaders.context(request),requestId,QueryParameters.from(request)));}
}
