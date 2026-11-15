package com.rbac.rbac1.controller;

import static com.rbac.rbac1.service.AuthenticationFailure.Reason.UNAUTHENTICATED;

import com.rbac.rbac1.dto.SessionDtos;
import com.rbac.rbac1.service.AuthenticationFailure;
import com.rbac.rbac1.service.PermissionCheckService;
import com.rbac.rbac1.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

@Component
public class SessionInterceptor implements HandlerInterceptor {

  public static final String ACTOR = "rbac.sessionUser";
  public static final String AUDIENCE = "rbac.audience";

  private final PermissionCheckService permissions;
  private final SessionService sessions;

  public SessionInterceptor(SessionService sessions, PermissionCheckService permissions) {
    this.permissions = permissions;
    this.sessions = sessions;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    var token = bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
    var session = sessions.authenticate(token);
    request.setAttribute(ACTOR, session.actor());
    request.setAttribute(AUDIENCE, session.audience());
    if (request.getRequestURI().startsWith("/api/v1/")) {
      sessions.applicationContext(session.audience(), "permission-center");
      var actor = (SessionDtos.SessionUser) request.getAttribute(ACTOR);
      permissions.requireManagementApi(
          actor.id(),
          request.getMethod(),
          request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE).toString());
    }
    response.setHeader("Cache-Control", "no-store");
    return true;
  }

  static String bearerToken(String authorization) {
    if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
      throw new AuthenticationFailure(UNAUTHENTICATED);
    }
    var token = authorization.substring(7).strip();
    if (token.isEmpty()) {
      throw new AuthenticationFailure(UNAUTHENTICATED);
    }
    return token;
  }
}
