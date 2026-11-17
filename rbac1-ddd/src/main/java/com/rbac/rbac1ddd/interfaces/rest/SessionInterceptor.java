package com.rbac.rbac1ddd.interfaces.rest;

import com.rbac.rbac1ddd.application.auth.AccessApplicationService;
import com.rbac.rbac1ddd.application.auth.AuthenticatedSession;
import com.rbac.rbac1ddd.application.auth.AuthenticationFailure;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 受保护请求先认证令牌，再把登录会话和当前操作者放入请求属性。
 *
 * <p>覆盖范围与 rbac1-structured 相同：{@code /api/v1/**} 和除登录外的 {@code /auth/**}。
 * 只有 {@code /api/v1/**} 是权限中心的管理接口，额外要求令牌适用于权限中心并检查接口权限。
 */
@Component
@RequiredArgsConstructor
public class SessionInterceptor implements HandlerInterceptor {

  /** 请求属性名：保存认证通过后的当前操作者，供 Controller 用 {@code @RequestAttribute} 读取。 */
  public static final String OPERATOR = "rbac.operator";

  /** 请求属性名：保存认证通过后的登录会话（操作者和令牌受众）。 */
  public static final String SESSION = "rbac.session";

  /** 权限中心管理接口的路径前缀。 */
  static final String MANAGEMENT_API_PREFIX = "/api/v1/";

  private final AccessApplicationService access;

  /**
   * 认证请求中的 Bearer 令牌；管理接口还按匹配到的路由模板检查接口权限。
   *
   * <p>认证通过后先放入请求属性再检查接口权限，因此接口权限不足的写操作也能按操作者记录失败日志
   * （与 rbac1-structured 相同）。认证或授权失败时由应用服务抛出异常，交给统一异常处理返回 401/403；
   * 成功时响应禁止缓存。
   */
  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    AuthenticatedSession session =
        access.authenticate(bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION)));
    request.setAttribute(SESSION, session);
    request.setAttribute(OPERATOR, session.operator());
    if (isManagementApi(request)) {
      access.requireManagementApi(
          session,
          request.getMethod(),
          String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)));
    }
    response.setHeader("Cache-Control", "no-store");
    return true;
  }

  /** 判断请求是否是权限中心的管理接口。 */
  static boolean isManagementApi(HttpServletRequest request) {
    return request.getRequestURI().startsWith(MANAGEMENT_API_PREFIX);
  }

  /**
   * 从 Authorization 头中取出令牌。
   *
   * <p>前缀 {@code Bearer } 不区分大小写；缺少前缀或令牌为空时视为未认证。
   */
  static String bearerToken(String authorization) {
    if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
      throw new AuthenticationFailure();
    }
    String token = authorization.substring(7).strip();
    if (token.isEmpty()) {
      throw new AuthenticationFailure();
    }
    return token;
  }
}
