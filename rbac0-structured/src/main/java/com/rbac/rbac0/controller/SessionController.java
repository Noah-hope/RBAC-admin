package com.rbac.rbac0.controller;

import com.rbac.rbac0.dto.SessionDtos.LoginRequest;
import com.rbac.rbac0.dto.SessionDtos.LoginResult;
import com.rbac.rbac0.dto.SessionDtos.SessionUser;
import com.rbac.rbac0.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.rbac.rbac0.controller.SessionInterceptor.bearerToken;

@RestController
@RequestMapping("/auth")
public class SessionController {

    private final com.rbac.rbac0.service.PermissionCheckService permissions;
    private final SessionService sessions;

    public SessionController(SessionService sessions, com.rbac.rbac0.service.PermissionCheckService permissions) {
        this.permissions = permissions;
        this.sessions = sessions;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResult> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(sessions.login(request, http.getRemoteAddr()));
    }

    @GetMapping("/me")
    public com.rbac.rbac0.service.PermissionCheckService.AuthUser me(
            @org.springframework.web.bind.annotation.RequestAttribute(SessionInterceptor.ACTOR) SessionUser actor,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value="X-Application-Code",required=false) String app) {
        return permissions.current(actor,sessions.applicationContext(bearerToken(authorization),app));
    }
    @GetMapping("/permissions/{permission}")
    public com.rbac.rbac0.dto.AuthorizationDtos.PermissionCheckResult frontend(
            @org.springframework.web.bind.annotation.RequestAttribute(SessionInterceptor.ACTOR) SessionUser actor,
            @org.springframework.web.bind.annotation.PathVariable @jakarta.validation.constraints.Pattern(regexp=com.rbac.rbac0.dto.ApplicationDtos.PERMISSION_CODE) String permission,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value="X-Application-Code",required=false) String app) {
        return permissions.checkPermission(actor.id(),sessions.applicationContext(bearerToken(authorization),app),permission);
    }
    @GetMapping("/api-permissions/{operationId}")
    public com.rbac.rbac0.service.PermissionCheckService.ApiCheckResult api(
            @org.springframework.web.bind.annotation.RequestAttribute(SessionInterceptor.ACTOR) SessionUser actor,
            @org.springframework.web.bind.annotation.PathVariable @jakarta.validation.constraints.Size(min=1,max=128) String operationId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(value="X-Application-Code",required=false) String app) {
        return permissions.checkApi(actor.id(),sessions.applicationContext(bearerToken(authorization),app),operationId);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        sessions.logout(bearerToken(authorization));
        return ResponseEntity.noContent().build();
    }

}
