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
@RequestMapping("/api/v1/admin/employees/{employeeId}")
public class EmployeeStateController {
    private final LifecycleService service;
    public EmployeeStateController(LifecycleService service){this.service=service;}
    @PostMapping("/state-previews")
    public ResponseEntity<?> preview(@AuthenticationPrincipal Jwt token,@PathVariable String employeeId,@RequestBody LifecycleInput.EmployeeState input)throws Exception{return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.employeePreview(token,employeeId,input));}
    @PutMapping("/state")
    public ResponseEntity<Object> save(@AuthenticationPrincipal Jwt token,@PathVariable String employeeId,@RequestHeader("Idempotency-Key")String key,@RequestBody LifecycleInput.EmployeeSave input,HttpServletRequest request)throws Exception{return IdempotencyHttp.response(service.employeeState(token,employeeId,key,input,(String)request.getAttribute("rbac.requestId")),request);}
}
