package com.rbac.rbac1ddd.application.auth;

import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.FORBIDDEN;
import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.NOT_FOUND;

import com.rbac.rbac1ddd.domain.app.BusinessApplication;
import com.rbac.rbac1ddd.domain.app.BusinessApplicationRepository;
import com.rbac.rbac1ddd.domain.permission.ManagementApi;
import com.rbac.rbac1ddd.domain.permission.ManagementApiRepository;
import com.rbac.rbac1ddd.domain.permission.PermissionCatalog;
import com.rbac.rbac1ddd.domain.permission.PermissionRepository;
import com.rbac.rbac1ddd.domain.role.RoleHierarchy;
import com.rbac.rbac1ddd.domain.role.RoleRepository;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.user.User;
import com.rbac.rbac1ddd.domain.user.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用服务：身份认证与权限检查。
 *
 * <p>认证每个受保护请求的令牌；为权限中心自身的管理接口检查令牌受众和接口权限；
 * 为接入系统提供当前用户信息、菜单按钮权限检查和接口权限检查。
 * 每次都重新计算用户的继承有效权限，因此角色或继承关系修改后，下一次权限检查立即生效。
 */
@Service
@RequiredArgsConstructor
public class AccessApplicationService {

  private final TokenVerifier tokens;
  private final SessionStore sessions;
  private final UserRepository users;
  private final RoleRepository roles;
  private final PermissionRepository permissions;
  private final BusinessApplicationRepository applications;
  private final ManagementApiRepository apis;

  /**
   * 认证令牌，得到当前操作者和令牌适用的应用。
   *
   * <p>令牌签名有效、会话未失效、账号可用且令牌主体与会话用户一致才算认证通过。
   *
   * @param token 请求携带的 Bearer 令牌
   * @return 认证通过的登录会话
   * @throws AuthenticationFailure 任一条件不满足时
   */
  @Transactional(readOnly = true)
  public AuthenticatedSession authenticate(String token) {
    VerifiedToken verified = tokens.verify(token);
    Long userId = sessions.findActiveUserId(token).orElseThrow(AuthenticationFailure::new);
    User user =
        users.findById(userId).filter(User::isActive).orElseThrow(AuthenticationFailure::new);
    if (!Long.toString(user.getId()).equals(verified.subject())) throw new AuthenticationFailure();
    return new AuthenticatedSession(
        new Operator(
            user.getId(),
            user.getUsername(),
            user.getName(),
            user.getDepartmentId(),
            user.managementRole(roles.loadHierarchy())),
        verified.audience());
  }

  /**
   * 检查已认证的用户能否调用权限中心的指定管理接口。
   *
   * <p>令牌必须适用于权限中心；管理接口必须已登记，且是自助接口或用户的继承有效权限覆盖该接口。
   *
   * @param session 认证通过的登录会话
   * @param method HTTP 方法
   * @param route Spring MVC 匹配到的路由模板，例如 /api/v1/roles/{roleId}
   * @throws DomainException 令牌不适用于权限中心时为 APPLICATION_MISMATCH；接口未登记或没有权限时为 FORBIDDEN
   */
  @Transactional(readOnly = true)
  public void requireManagementApi(AuthenticatedSession session, String method, String route) {
    session.applicationContext(BusinessApplication.PERMISSION_CENTER);
    BusinessApplication center = requireApplication(BusinessApplication.PERMISSION_CENTER);
    ManagementApi api =
        apis.findByRoute(center.getCode(), method, route)
            .orElseThrow(() -> new DomainException(FORBIDDEN, "管理API未登记。"));
    if (!allowsApi(session, api)) {
      throw new DomainException(FORBIDDEN, "没有该接口的调用权限。");
    }
  }

  /**
   * 当前用户在目标应用中的身份、角色和有效权限编码。
   *
   * @param session 认证通过的登录会话
   * @param requestedCode 请求指定的目标应用编码，可以为 null
   * @return 当前用户信息
   */
  @Transactional(readOnly = true)
  public CurrentUserResult currentUser(AuthenticatedSession session, String requestedCode) {
    BusinessApplication application =
        requireApplication(session.applicationContext(requestedCode));
    User user = userOf(session);
    RoleHierarchy hierarchy = roles.loadHierarchy();
    PermissionCatalog catalog = permissions.loadCatalog();
    Operator operator = session.operator();
    return new CurrentUserResult(
        Long.toString(operator.id()),
        user.rolesGrantingPermissionsIn(application.getId(), hierarchy, catalog).stream()
            .map(String::valueOf)
            .toList(),
        catalog.codesIn(application.getId(), user.effectivePermissionIds(hierarchy)),
        operator.name(),
        operator.username(),
        operator.id(),
        operator.departmentId(),
        operator.managementRole().name());
  }

  /**
   * 检查当前用户在目标应用中是否拥有指定的菜单或按钮权限，包含继承得到的权限。
   *
   * @param session 认证通过的登录会话
   * @param requestedCode 请求指定的目标应用编码，可以为 null
   * @param permissionCode 权限编码
   * @return 检查结果
   */
  @Transactional(readOnly = true)
  public PermissionCheckResult checkPermission(
      AuthenticatedSession session, String requestedCode, String permissionCode) {
    BusinessApplication application =
        requireApplication(session.applicationContext(requestedCode));
    List<String> granted =
        permissions
            .loadCatalog()
            .codesIn(
                application.getId(), userOf(session).effectivePermissionIds(roles.loadHierarchy()));
    return new PermissionCheckResult(granted.contains(permissionCode));
  }

  /**
   * 检查当前用户能否调用目标应用中登记的接口。
   *
   * @param session 认证通过的登录会话
   * @param requestedCode 请求指定的目标应用编码，可以为 null
   * @param operationId 接口的操作标识
   * @return 检查结果
   * @throws DomainException 接口未登记时为 NOT_FOUND
   */
  @Transactional(readOnly = true)
  public ApiCheckResult checkApi(
      AuthenticatedSession session, String requestedCode, String operationId) {
    BusinessApplication application =
        requireApplication(session.applicationContext(requestedCode));
    ManagementApi api =
        apis.findByOperationId(application.getCode(), operationId)
            .orElseThrow(() -> new DomainException(NOT_FOUND, "API未登记。"));
    return new ApiCheckResult(
        operationId,
        allowsApi(session, api));
  }

  /** 身份目录仅开放给目标应用中持有对应 API 有效权限的调用人。 */
  @Transactional(readOnly = true)
  public void requireIdentityDirectoryApi(
      AuthenticatedSession session, String requestedCode, String operationId) {
    BusinessApplication application =
        requireApplication(session.applicationContext(requestedCode));
    boolean allowed =
        apis.findByOperationId(application.getCode(), operationId)
            .filter(api -> allowsApi(session, api))
            .isPresent();
    if (!allowed) {
      throw new DomainException(FORBIDDEN, "没有该接口的调用权限。");
    }
  }

  /** 使用包含角色继承的有效权限执行 API 授权规则。 */
  private boolean allowsApi(AuthenticatedSession session, ManagementApi api) {
    return api.allows(userOf(session).effectivePermissionIds(roles.loadHierarchy()));
  }

  /** 按编码取已登记的应用，不存在时为 NOT_FOUND。 */
  private BusinessApplication requireApplication(String code) {
    return applications
        .findByCode(code)
        .orElseThrow(() -> new DomainException(NOT_FOUND, "应用不存在。"));
  }

  /** 重新读取当前操作者的用户聚合，用于计算有效权限。 */
  private User userOf(AuthenticatedSession session) {
    return users.findById(session.operator().id()).orElseThrow(AuthenticationFailure::new);
  }
}
