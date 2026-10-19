package com.adgn.backend.web.identity.controller;

import com.adgn.backend.identity.service.IdentityService;
import com.adgn.backend.web.identity.dto.IdentityDtos;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
@RequestMapping("/api/v1/admin")
public class IdentityController {
    private final IdentityService service;
    public IdentityController(IdentityService service){this.service=service;}
    @PostMapping("/sessions")
    public ResponseEntity<IdentityDtos.Session> login(@RequestBody IdentityDtos.Login input,HttpServletRequest request) throws Exception {
        var result=service.login(input.loginName(),input.password(),request.getRemoteAddr(),id(request));
        var s=result.session();
        return ResponseEntity.created(URI.create("/api/v1/admin/sessions/current")).cacheControl(org.springframework.http.CacheControl.noStore())
                .body(new IdentityDtos.Session(s.sessionId(),s.accountId(),s.employeeId(),s.identity(),s.mode(),result.accessToken(),"Bearer",s.expiresAt()));
    }
    @GetMapping("/sessions/current")
    public IdentityService.SessionView current(@AuthenticationPrincipal Jwt token) throws Exception {return service.current(token);}
    @DeleteMapping("/sessions/current")
    public ResponseEntity<Void> logout(HttpServletRequest request) throws Exception {
        String header=request.getHeader("Authorization");
        String raw=header!=null && header.regionMatches(true,0,"Bearer ",0,7) ? header.substring(7) : null;
        if(java.util.Collections.list(request.getHeaders("Authorization")).size()>1)raw=null;
        service.logout(raw,id(request)); return ResponseEntity.noContent().build();
    }
    @PutMapping("/password")
    public ResponseEntity<Void> password(@AuthenticationPrincipal Jwt token,@RequestBody IdentityDtos.PasswordChange input,HttpServletRequest request) throws Exception {
        service.changePassword(token,input.currentPassword(),input.newPassword(),input.confirmPassword(),id(request));
        return ResponseEntity.noContent().build();
    }
    private static String id(HttpServletRequest request){return (String)request.getAttribute("rbac.requestId");}
}
