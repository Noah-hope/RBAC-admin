package com.adgn.backend.web.identity.controller;

import com.adgn.backend.identity.service.AccountService;
import com.adgn.backend.identity.repository.AccountRepository;
import com.adgn.backend.web.IdempotencyHttp;
import com.adgn.backend.web.identity.dto.AccountDtos;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
@RequestMapping("/api/v1/admin/accounts")
public class AccountController {
    private final AccountService service;
    public AccountController(AccountService service){this.service=service;}
    @GetMapping
    public ResponseEntity<AccountRepository.Page> list(@AuthenticationPrincipal Jwt token,
            @RequestParam(required=false)String page,@RequestParam(required=false)String pageSize,
            @RequestParam(required=false)String employeeId,@RequestParam(required=false)String q,
            @RequestParam(required=false)String enabled)throws Exception{
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(service.list(token,page,pageSize,employeeId,q,enabled));
    }
    @GetMapping("/{accountId}")
    public ResponseEntity<AccountRepository.View> get(@AuthenticationPrincipal Jwt token,@PathVariable String accountId)throws Exception{
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(service.get(token,accountId));
    }
    @PostMapping
    public ResponseEntity<Object> create(@AuthenticationPrincipal Jwt token,@RequestHeader("Idempotency-Key")String key,
            @RequestBody AccountDtos.Create input,HttpServletRequest request)throws Exception{
        return IdempotencyHttp.response(service.create(token,key,input.employeeId(),input.loginName(),input.temporaryPassword(),input.confirmPassword(),input.reason(),(String)request.getAttribute("rbac.requestId")),request);
    }
    @PutMapping("/{accountId}/state")
    public ResponseEntity<Object> state(@AuthenticationPrincipal Jwt token,@PathVariable String accountId,@RequestHeader("Idempotency-Key")String key,
            @RequestBody AccountDtos.State input,HttpServletRequest request)throws Exception{
        return IdempotencyHttp.response(service.state(token,key,accountId,input.expectedVersion(),input.enabled(),input.reason(),(String)request.getAttribute("rbac.requestId")),request);
    }
    @PutMapping("/{accountId}/password")
    public ResponseEntity<Object> reset(@AuthenticationPrincipal Jwt token,@PathVariable String accountId,@RequestHeader("Idempotency-Key")String key,
            @RequestBody AccountDtos.Reset input,HttpServletRequest request)throws Exception{
        return IdempotencyHttp.response(service.reset(token,key,accountId,input.expectedVersion(),input.temporaryPassword(),input.confirmPassword(),input.reason(),(String)request.getAttribute("rbac.requestId")),request);
    }
}
