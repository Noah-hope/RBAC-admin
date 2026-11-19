package com.rbac.rbac1ddd.interfaces.rest;

import static com.rbac.rbac1ddd.interfaces.rest.SessionInterceptor.SESSION;

import com.rbac.rbac1ddd.application.auth.AccessApplicationService;
import com.rbac.rbac1ddd.application.auth.AuthenticatedSession;
import com.rbac.rbac1ddd.application.auth.LoginCommand;
import com.rbac.rbac1ddd.application.auth.SessionApplicationService;
import com.rbac.rbac1ddd.interfaces.rest.dto.LoginDTO;
import com.rbac.rbac1ddd.interfaces.rest.vo.ApiCheckVO;
import com.rbac.rbac1ddd.interfaces.rest.vo.CurrentUserVO;
import com.rbac.rbac1ddd.interfaces.rest.vo.LoginVO;
import com.rbac.rbac1ddd.interfaces.rest.vo.PermissionCheckVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
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

/**
 * 统一身份接口：登录、退出、当前用户信息和权限检查，供权限中心前端和接入的业务系统使用。
 *
 * <p>除登录外都由 {@link SessionInterceptor} 先认证令牌；目标应用由 X-Application-Code 请求头指定，
 * 未指定时取令牌唯一的受众。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class SessionController {

  /** 指定目标应用编码的请求头。 */
  static final String APPLICATION_HEADER = "X-Application-Code";

  private final SessionApplicationService sessions;
  private final AccessApplicationService access;

  /** 验证账号密码并签发访问令牌，响应禁止缓存。 */
  @PostMapping("/login")
  public ResponseEntity<LoginVO> login(
      @Valid
      @RequestBody
      LoginDTO request,
      HttpServletRequest http) {
    LoginCommand command =
        new LoginCommand(request.username(), request.password(), request.systems());
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(LoginVO.from(sessions.login(command, http.getRemoteAddr())));
  }

  /** 查询当前用户在目标应用中的身份、角色和有效权限。 */
  @GetMapping("/me")
  public CurrentUserVO me(
      @RequestAttribute(SESSION)
      AuthenticatedSession session,
      @RequestHeader(value = APPLICATION_HEADER, required = false)
      String application) {
    return CurrentUserVO.from(access.currentUser(session, application));
  }

  /** 检查当前用户在目标应用中是否拥有指定的菜单或按钮权限；编码格式由接入系统决定，未登记的编码返回 false。 */
  @GetMapping("/permissions/{permission}")
  public PermissionCheckVO permission(
      @RequestAttribute(SESSION)
      AuthenticatedSession session,
      @PathVariable
      @Size(max = 64)
      String permission,
      @RequestHeader(value = APPLICATION_HEADER, required = false)
      String application) {
    return PermissionCheckVO.from(access.checkPermission(session, application, permission));
  }

  /** 检查当前用户能否调用目标应用中登记的接口。 */
  @GetMapping("/api-permissions/{operationId}")
  public ApiCheckVO api(
      @RequestAttribute(SESSION)
      AuthenticatedSession session,
      @PathVariable
      @Size(min = 1, max = 128)
      String operationId,
      @RequestHeader(value = APPLICATION_HEADER, required = false)
      String application) {
    return ApiCheckVO.from(access.checkApi(session, application, operationId));
  }

  /** 退出：只撤销当前令牌，成功返回 204。 */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
      String authorization) {
    sessions.logout(SessionInterceptor.bearerToken(authorization));
    return ResponseEntity.noContent().build();
  }
}
