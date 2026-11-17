package com.rbac.rbac1ddd.application.auth;

import com.rbac.rbac1ddd.application.role.RoleRefResult;
import java.util.List;

/**
 * 当前用户的权限（MyPermissions）：显式分配的业务角色，以及按应用汇总的已获菜单和按钮。
 *
 * @param businessRoles 显式分配的业务角色，按 ID 升序，不含祖先角色和预置管理角色
 * @param applications 全部已登记应用，按 ID 升序；没有权限的应用菜单为空
 */
public record MyPermissionsResult(
    List<RoleRefResult> businessRoles, List<MyPermissionsResult.Application> applications) {

  /**
   * 一个应用中的已获权限（MyApplicationPermissions）。
   *
   * @param applicationId 应用 ID
   * @param applicationName 应用名称
   * @param applicationCode 应用编码
   * @param menus 已获菜单，按 ID 升序
   */
  public record Application(
      long applicationId, String applicationName, String applicationCode, List<Menu> menus) {}

  /**
   * 已获菜单（MyMenu）。
   *
   * @param id 菜单权限 ID
   * @param name 菜单名称
   * @param code 菜单权限标识
   * @param sourceRoles 直接配置该菜单的角色，按 ID 升序
   * @param buttons 菜单下已获得的按钮，按 ID 升序
   */
  public record Menu(
      long id, String name, String code, List<RoleRefResult> sourceRoles, List<Button> buttons) {}

  /**
   * 已获按钮（MyPermissionRef）。
   *
   * @param id 按钮权限 ID
   * @param name 按钮名称
   * @param code 按钮权限标识
   * @param sourceRoles 直接配置该按钮的角色，按 ID 升序
   */
  public record Button(long id, String name, String code, List<RoleRefResult> sourceRoles) {}
}
