package com.adgn.backend.web.imports.controller;

import com.adgn.backend.imports.*;
import com.adgn.backend.web.IdempotencyHttp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/admin/import-batches")
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
public class ImportBatchController {
    private final ImportPlanService service;private final ImportCommitService commits;
    public ImportBatchController(ImportPlanService service,ImportCommitService commits){this.service=service;this.commits=commits;}
    @PostMapping("/{batchId}/commits") public ResponseEntity<Object> commit(@AuthenticationPrincipal Jwt token,@PathVariable String batchId,@RequestHeader("Idempotency-Key")String key,@RequestBody JsonNode input,HttpServletRequest request)throws Exception{return IdempotencyHttp.response(commits.commit(token,batchId,key,input,(String)request.getAttribute("rbac.requestId")),request);}
    @PostMapping public ResponseEntity<Object> precheck(@AuthenticationPrincipal Jwt token,@RequestHeader("Idempotency-Key")String key,@RequestBody JsonNode input,HttpServletRequest request)throws Exception{return IdempotencyHttp.response(service.precheck(token,key,input,(String)request.getAttribute("rbac.requestId")),request);}
    @GetMapping public ResponseEntity<ImportPlan.Page<ImportPlan.Batch>> list(@AuthenticationPrincipal Jwt token,@RequestParam(required=false)String sourceBatchId,@RequestParam(required=false)String state,@RequestParam(required=false)String page,@RequestParam(required=false)String pageSize)throws Exception{return safe(service.list(token,sourceBatchId,state,page,pageSize));}
    @GetMapping("/{batchId}") public ResponseEntity<ImportPlan.Batch> get(@AuthenticationPrincipal Jwt token,@PathVariable String batchId)throws Exception{return safe(service.get(batchId,token));}
    @GetMapping("/{batchId}/issues") public ResponseEntity<ImportPlan.Page<ImportPlan.Issue>> issues(@AuthenticationPrincipal Jwt token,@PathVariable String batchId,@RequestParam(required=false)String severity,@RequestParam(required=false)String page,@RequestParam(required=false)String pageSize)throws Exception{return safe(service.issues(token,batchId,severity,page,pageSize));}
    @GetMapping("/{batchId}/changes") public ResponseEntity<ImportPlan.Page<ImportPlanService.ChangeView>> changes(@AuthenticationPrincipal Jwt token,@PathVariable String batchId,@RequestParam(required=false)String page,@RequestParam(required=false)String pageSize)throws Exception{return safe(service.changes(token,batchId,page,pageSize));}
    private static <T> ResponseEntity<T> safe(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
