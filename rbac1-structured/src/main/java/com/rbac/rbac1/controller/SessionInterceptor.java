package com.rbac.rbac1.controller;

import static com.rbac.rbac1.service.AuthenticationFailure.Reason.UNAUTHENTICATED;

import com.rbac.rbac1.entity.BusinessApplication;
import com.rbac.rbac1.service.AuthenticationFailure;
import com.rbac.rbac1.service.PermissionCheckService;
import com.rbac.rbac1.service.SessionService;
import com.rbac.rbac1.service.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 会话拦截器：校验 Bearer 令牌并把当前用户和令牌受众放入请求属性；管理接口还要求令牌适用于权限中心，且用户拥有该接口的权限。
 */
@Component
@RequiredArgsConstructor
public class SessionInterceptor implements HandlerInterceptor {

  /** 请求属性名：当前登录用户 {@link SessionUser}。 */
  public static final String ACTOR = "rbac.sessionUser";

  /** 请求属性名：令牌受众，即令牌适用的应用编码列表。 */
  public static final String AUDIENCE = "rbac.audience";

  private final PermissionCheckService permissions;
  private final SessionService sessions;

  /**
   * 认证请求并做接口级权限检查，通过后禁止缓存响应。
   *
   * <p>管理接口（/api/v1/**）按匹配到的路由模板检查权限，而不是按实际 URI，这样带路径变量的接口只需登记一次。
   */
  @Override
  public boolean preHandle(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler) {
    String token = bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
    SessionService.AuthenticatedSession session = sessions.authenticate(token);
    request.setAttribute(ACTOR, session.actor());
    request.setAttribute(AUDIENCE, session.audience());
    if (request.getRequestURI().startsWith("/api/v1/")) {
      sessions.applicationContext(session.audience(), BusinessApplication.PERMISSION_CENTER);
      SessionUser actor = (SessionUser) request.getAttribute(ACTOR);
      permissions.requireManagementApi(
          actor.id(),
          request.getMethod(),
          request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE).toString());
    }
    response.setHeader("Cache-Control", "no-store");
    return true;
  }

  /**
   * 从 Authorization 头取出 Bearer 令牌；方案名不区分大小写，缺少头、方案不符或令牌为空都视为未认证。
   *
   * @param authorization Authorization 请求头，可能为空
   * @return 去掉首尾空白的令牌原文
   */
  static String bearerToken(String authorization) {
    if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
      throw new AuthenticationFailure(UNAUTHENTICATED);
    }
    String token = authorization.substring(7).strip();
    if (token.isEmpty()) {
      throw new AuthenticationFailure(UNAUTHENTICATED);
    }
    return token;
  }
}
