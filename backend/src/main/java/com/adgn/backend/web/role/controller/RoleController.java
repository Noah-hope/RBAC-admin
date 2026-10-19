package com.adgn.backend.web.role.controller;

import com.adgn.backend.role.repository.RoleRepository;
import com.adgn.backend.role.service.RoleService;
import com.adgn.backend.web.IdempotencyHttp;
import com.adgn.backend.web.management.ManagementHeaders;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
@RequestMapping("/api/v1/admin/roles")
public class RoleController {
    public record Metadata(Long expectedVersion,String name,String description,String reason) { }
    private final RoleService service;
    public RoleController(RoleService service){this.service=service;}
    @GetMapping public ResponseEntity<RoleRepository.Page> list(@AuthenticationPrincipal Jwt token,HttpServletRequest request,
            @RequestParam(required=false)String page,@RequestParam(required=false)String pageSize,@RequestParam(required=false)String companyId,
            @RequestParam(required=false)String departmentId,@RequestParam(required=false)String systemId,@RequestParam(required=false)String state,@RequestParam(required=false)String q)throws Exception{
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(token,ManagementHeaders.context(request),page,pageSize,companyId,departmentId,systemId,state,q));
    }
    @GetMapping("/{roleId}") public ResponseEntity<RoleRepository.View> get(@AuthenticationPrincipal Jwt token,@PathVariable String roleId,HttpServletRequest request)throws Exception{
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(token,ManagementHeaders.context(request),roleId));
    }
    @PutMapping("/{roleId}") public ResponseEntity<Object> metadata(@AuthenticationPrincipal Jwt token,@PathVariable String roleId,@RequestHeader("Idempotency-Key")String key,@RequestBody Metadata input,HttpServletRequest request)throws Exception{
        return IdempotencyHttp.response(service.metadata(token,ManagementHeaders.context(request),roleId,key,input.expectedVersion(),input.name(),input.description(),input.reason(),(String)request.getAttribute("rbac.requestId")),request);
    }
}
