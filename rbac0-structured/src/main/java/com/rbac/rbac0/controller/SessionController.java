package com.rbac.rbac0.controller;

import static com.rbac.rbac0.controller.SessionInterceptor.bearerToken;

import com.rbac.rbac0.dto.ApplicationDtos;
import com.rbac.rbac0.dto.AuthorizationDtos;
import com.rbac.rbac0.dto.SessionDtos.LoginRequest;
import com.rbac.rbac0.dto.SessionDtos.LoginResult;
import com.rbac.rbac0.dto.SessionDtos.SessionUser;
import com.rbac.rbac0.service.PermissionCheckService;
import com.rbac.rbac0.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class SessionController {

  private final PermissionCheckService permissions;
  private final SessionService sessions;

  public SessionController(SessionService sessions, PermissionCheckService permissions) {
    this.permissions = permissions;
    this.sessions = sessions;
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResult> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest http) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(sessions.login(request, http.getRemoteAddr()));
  }

  @GetMapping("/me")
  public PermissionCheckService.AuthUser me(
      @RequestAttribute(SessionInterceptor.ACTOR) SessionUser actor,
      @RequestAttribute(SessionInterceptor.AUDIENCE) List<String> audience,
      @RequestHeader(value = "X-Application-Code", required = false) String app) {
    return permissions.current(actor, sessions.applicationContext(audience, app));
  }

  @GetMapping("/permissions/{permission}")
  public AuthorizationDtos.PermissionCheckResult frontend(
      @RequestAttribute(SessionInterceptor.ACTOR) SessionUser actor,
      @PathVariable @Pattern(regexp = ApplicationDtos.PERMISSION_CODE) String permission,
      @RequestAttribute(SessionInterceptor.AUDIENCE) List<String> audience,
      @RequestHeader(value = "X-Application-Code", required = false) String app) {
    return permissions.checkPermission(
        actor.id(), sessions.applicationContext(audience, app), permission);
  }

  @GetMapping("/api-permissions/{operationId}")
  public PermissionCheckService.ApiCheckResult api(
      @RequestAttribute(SessionInterceptor.ACTOR) SessionUser actor,
      @PathVariable @Size(min = 1, max = 128) String operationId,
      @RequestAttribute(SessionInterceptor.AUDIENCE) List<String> audience,
      @RequestHeader(value = "X-Application-Code", required = false) String app) {
    return permissions.checkApi(
        actor.id(), sessions.applicationContext(audience, app), operationId);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
    sessions.logout(bearerToken(authorization));
    return ResponseEntity.noContent().build();
  }
}
