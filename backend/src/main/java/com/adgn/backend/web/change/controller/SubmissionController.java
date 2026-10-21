package com.adgn.backend.web.change.controller;
import com.adgn.backend.change.SubmissionService;
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
@RequestMapping("/api/v1/admin/change-requests/{requestId}")
public class SubmissionController {
    private final SubmissionService service;
    private final com.adgn.backend.oa.OaDeliveryService delivery;
    public SubmissionController(SubmissionService service,com.adgn.backend.oa.OaDeliveryService delivery){this.service=service;this.delivery=delivery;}
    @PostMapping("/preview") public ResponseEntity<Object> preview(@AuthenticationPrincipal Jwt jwt,@PathVariable String requestId,@RequestBody JsonNode input,HttpServletRequest r)throws Exception{return ResponseEntity.ok().header("Cache-Control","no-store").body(service.preview(jwt,ManagementHeaders.context(r),requestId,input));}
    @PostMapping("/submissions") public ResponseEntity<Object> submit(@AuthenticationPrincipal Jwt jwt,@PathVariable String requestId,@RequestHeader("Idempotency-Key")String key,@RequestBody JsonNode input,HttpServletRequest r)throws Exception{return IdempotencyHttp.response(service.submit(jwt,ManagementHeaders.context(r),requestId,key,input,(String)r.getAttribute("rbac.requestId")),r);}
    @PostMapping("/delivery-retries") public ResponseEntity<Object> retry(@AuthenticationPrincipal Jwt jwt,@PathVariable String requestId,@RequestHeader("Idempotency-Key")String key,@RequestBody JsonNode input,HttpServletRequest r)throws Exception{return IdempotencyHttp.response(delivery.retry(jwt,ManagementHeaders.context(r),requestId,key,input),r);}
}
