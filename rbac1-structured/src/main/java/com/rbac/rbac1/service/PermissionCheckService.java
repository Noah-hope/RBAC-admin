package com.rbac.rbac1.service;

import com.rbac.rbac1.entity.Api;
import com.rbac.rbac1.entity.BusinessApplication;
import com.rbac.rbac1.entity.Permission;
import com.rbac.rbac1.entity.Role;
import com.rbac.rbac1.mapper.ApiMapper;
import com.rbac.rbac1.mapper.ApiPermissionMapper;
import com.rbac.rbac1.mapper.ApplicationMapper;
import com.rbac.rbac1.vo.ApiCheckVO;
import com.rbac.rbac1.vo.CurrentUserVO;
import com.rbac.rbac1.vo.MyApplicationPermissionsVO;
import com.rbac.rbac1.vo.MyMenuVO;
import com.rbac.rbac1.vo.MyPermissionRefVO;
import com.rbac.rbac1.vo.MyPermissionsVO;
import com.rbac.rbac1.vo.PermissionCheckVO;
import com.rbac.rbac1.vo.RoleRefVO;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 权限检查：供业务系统查询当前用户的权限、校验权限编码和 API 调用权限，也守卫本系统的管理接口。
 */
@Service
@RequiredArgsConstructor
public class PermissionCheckService {

  /** 本系统中任何已登录用户都能调用的自助接口，不要求登记权限。 */
  private static final Set<String> SELF_SERVICE =
      Set.of(
          "getMyPermissions",
          "getRequestablePermissions",
          "listPermissionRequests",
          "createPermissionRequest",
          "getPermissionRequest",
          "withdrawPermissionRequest");

  private final ApplicationMapper applications;

  private final RolePermissionService effective;

  private final ApiMapper apis;

  private final ApiPermissionMapper links;

  /**
   * 当前用户的业务角色和按应用组织的菜单、按钮权限，每项都列出来源角色。
   *
   * @param userId 用户 ID
   * @return 我的权限
   */
  @Transactional(readOnly = true)
  public MyPermissionsVO mine(long userId) {
    RolePermissionService.UserPermissions user = effective.forUser(userId);
    RolePermissionService.Graph graph = user.graph();
    List<RoleRefVO> assigned =
        effective.references(
            graph,
            user.roleIds().stream()
                .filter(id -> Role.BUSINESS.equals(graph.roles().get(id).getRoleType()))
                .toList());
    List<MyApplicationPermissionsVO> trees =
        graph.applications().values().stream()
            .sorted(Comparator.comparing(a -> a.getId()))
            .map(
                app -> {
                  List<MyMenuVO> menus =
                      user.sources().keySet().stream()
                          .map(graph.permissions()::get)
                          .filter(
                              p ->
                                  p.getApplicationId().equals(app.getId())
                                      && Permission.MENU.equals(p.getType()))
                          .map(
                              menu ->
                                  new MyMenuVO(
                                      menu.getId(),
                                      menu.getName(),
                                      menu.getCode(),
                                      effective.references(graph, user.sources().get(menu.getId())),
                                      user.sources().keySet().stream()
                                          .map(graph.permissions()::get)
                                          .filter(button -> menu.getId().equals(button.getParentId()))
                                          .map(
                                              button ->
                                                  new MyPermissionRefVO(
                                                      button.getId(),
                                                      button.getName(),
                                                      button.getCode(),
                                                      effective.references(
                                                          graph, user.sources().get(button.getId()))))
                                          .toList()))
                          .toList();
                  return new MyApplicationPermissionsVO(app.getId(), app.getName(), app.getCode(), menus);
                })
            .toList();
    return new MyPermissionsVO(assigned, trees);
  }

  /**
   * 业务系统登录后查询的当前用户信息：只返回与该应用有关的角色和权限编码。
   *
   * @param actor 当前操作人
   * @param code 应用编码
   * @return 当前用户信息
   * @throws ManagementFailure 应用不存在时为 NOT_FOUND
   */
  @Transactional(readOnly = true)
  public CurrentUserVO current(SessionUser actor, String code) {
    BusinessApplication app = requireApplication(code);
    RolePermissionService.UserPermissions user = effective.forUser(actor.id());
    RolePermissionService.Graph graph = user.graph();
    List<Permission> granted =
        user.sources().keySet().stream()
            .map(graph.permissions()::get)
            .filter(p -> p.getApplicationId().equals(app.getId()))
            .toList();
    List<String> assigned =
        user.roleIds().stream()
            .filter(
                id ->
                    effective.effectiveIds(graph, id).stream()
                        .anyMatch(
                            permission ->
                                graph.permissions().get(permission).getApplicationId().equals(app.getId())))
            .map(Object::toString)
            .toList();
    return new CurrentUserVO(
        Long.toString(actor.id()),
        assigned,
        granted.stream().map(p -> p.getCode()).distinct().toList(),
        actor.name(),
        actor.username(),
        actor.id(),
        actor.departmentId(),
        actor.managementRole());
  }

  /**
   * 判断用户在某应用中是否拥有指定权限编码。
   *
   * @param user 用户 ID
   * @param app 应用编码
   * @param code 权限编码
   * @return 检查结果
   * @throws ManagementFailure 应用不存在时为 NOT_FOUND
   */
  @Transactional(readOnly = true)
  public PermissionCheckVO checkPermission(long user, String app, String code) {
    BusinessApplication application = requireApplication(app);
    RolePermissionService.UserPermissions granted = effective.forUser(user);
    return new PermissionCheckVO(
        granted.sources().keySet().stream()
            .map(granted.graph().permissions()::get)
            .anyMatch(p -> p.getApplicationId().equals(application.getId()) && p.getCode().equals(code)));
  }

  /**
   * 判断用户能否调用某应用登记的 API：拥有任一关联权限即可调用。
   *
   * <p>本系统的自助接口对所有已登录用户开放，但仍要求 API 已登记。
   *
   * @param user 用户 ID
   * @param app 应用编码
   * @param operation API 的 operationId
   * @return 检查结果
   * @throws ManagementFailure 应用不存在或 API 未登记时为 NOT_FOUND
   */
  @Transactional(readOnly = true)
  public ApiCheckVO checkApi(long user, String app, String operation) {
    Api api =
        apis.findByApplicationIdAndOperationId(requireApplication(app).getId(), operation)
            .orElseThrow(
                () -> new ManagementFailure(ManagementFailure.Reason.NOT_FOUND, "API未登记。"));
    if (BusinessApplication.PERMISSION_CENTER.equals(app) && SELF_SERVICE.contains(operation)) {
      return new ApiCheckVO(operation, true);
    }
    Set<Long> permissionIds = effective.forUser(user).sources().keySet();
    return new ApiCheckVO(
        operation,
        links.findByApiId(api.getId()).stream().anyMatch(l -> permissionIds.contains(l.getPermissionId())));
  }

  /**
   * 守卫本系统的管理接口：按请求方法和路径找到登记的 API，再检查调用权限。
   *
   * @param user 用户 ID
   * @param method HTTP 方法
   * @param path 路径模板
   * @throws ManagementFailure API 未登记或没有调用权限时为 FORBIDDEN
   */
  @Transactional(readOnly = true)
  public void requireManagementApi(long user, String method, String path) {
    BusinessApplication app = requireApplication(BusinessApplication.PERMISSION_CENTER);
    Api api =
        apis.findByApplicationIdAndHttpMethodAndPath(app.getId(), method, path)
            .orElseThrow(
                () -> new ManagementFailure(ManagementFailure.Reason.FORBIDDEN, "管理API未登记。"));

    if (!checkApi(user, app.getCode(), api.getOperationId()).allowed()) {
      throw new ManagementFailure(ManagementFailure.Reason.FORBIDDEN, "没有该接口的调用权限。");
    }
  }

  /** 按编码查找应用，不存在时抛出 NOT_FOUND。 */
  private BusinessApplication requireApplication(String code) {
    return applications
        .findByCode(code)
        .orElseThrow(() -> new ManagementFailure(ManagementFailure.Reason.NOT_FOUND, "应用不存在。"));
  }
}
