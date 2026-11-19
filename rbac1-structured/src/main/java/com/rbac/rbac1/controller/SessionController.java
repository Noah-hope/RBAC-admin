package com.rbac.rbac1.controller;

import static com.rbac.rbac1.controller.SessionInterceptor.bearerToken;

import com.rbac.rbac1.dto.Formats;
import com.rbac.rbac1.dto.LoginDTO;
import com.rbac.rbac1.service.PermissionCheckService;
import com.rbac.rbac1.service.SessionService;
import com.rbac.rbac1.service.SessionUser;
import com.rbac.rbac1.vo.ApiCheckVO;
import com.rbac.rbac1.vo.CurrentUserVO;
import com.rbac.rbac1.vo.LoginVO;
import com.rbac.rbac1.vo.PermissionCheckVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
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
 * 统一身份认证接口：登录、注销、当前用户信息，以及供业务应用调用的前端权限和接口权限检查。
 *
 * <p>目标应用取自 X-Application-Code 请求头；不传时令牌只能适用于一个应用。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class SessionController {

  private final PermissionCheckService permissions;
  private final SessionService sessions;

  /** 账号密码登录，按客户端地址限制失败次数；响应含令牌，禁止缓存。 */
  @PostMapping("/login")
  public ResponseEntity<LoginVO> login(
      @Valid
      @RequestBody
      LoginDTO request,
      HttpServletRequest http) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(sessions.login(request, http.getRemoteAddr()));
  }

  /** 返回当前用户及其在目标应用中的权限。 */
  @GetMapping("/me")
  public CurrentUserVO me(
      @RequestAttribute(SessionInterceptor.ACTOR)
      SessionUser actor,
      @RequestAttribute(SessionInterceptor.AUDIENCE)
      List<String> audience,
      @RequestHeader(value = "X-Application-Code", required = false)
      String app) {
    return permissions.current(actor, sessions.applicationContext(audience, app));
  }

  /** 检查当前用户在目标应用中是否拥有某个前端权限编码。 */
  @GetMapping("/permissions/{permission}")
  public PermissionCheckVO frontend(
      @RequestAttribute(SessionInterceptor.ACTOR)
      SessionUser actor,
      @PathVariable
      @Pattern(regexp = Formats.PERMISSION_CODE)
      String permission,
      @RequestAttribute(SessionInterceptor.AUDIENCE)
      List<String> audience,
      @RequestHeader(value = "X-Application-Code", required = false)
      String app) {
    return permissions.checkPermission(
        actor.id(), sessions.applicationContext(audience, app), permission);
  }

  /** 检查当前用户在目标应用中是否可以调用某个已登记的接口。 */
  @GetMapping("/api-permissions/{operationId}")
  public ApiCheckVO api(
      @RequestAttribute(SessionInterceptor.ACTOR)
      SessionUser actor,
      @PathVariable
      @Size(min = 1, max = 128)
      String operationId,
      @RequestAttribute(SessionInterceptor.AUDIENCE)
      List<String> audience,
      @RequestHeader(value = "X-Application-Code", required = false)
      String app) {
    return permissions.checkApi(
        actor.id(), sessions.applicationContext(audience, app), operationId);
  }

  /** 注销当前令牌对应的会话，成功返回 204。 */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
      String authorization) {
    sessions.logout(bearerToken(authorization));
    return ResponseEntity.noContent().build();
  }
}
