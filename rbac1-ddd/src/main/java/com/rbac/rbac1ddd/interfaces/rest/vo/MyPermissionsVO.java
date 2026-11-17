package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.auth.MyPermissionsResult;
import java.util.List;

/**
 * 「我的权限」的响应体（MyPermissions），字段与 rbac1-structured 相同。
 *
 * @param businessRoles 显式分配的业务角色
 * @param applications 各应用的已获权限
 */
public record MyPermissionsVO(
    List<RoleRefVO> businessRoles, List<MyPermissionsVO.MyApplicationPermissions> applications) {

  /** 由应用层结果逐层转换。 */
  public static MyPermissionsVO from(MyPermissionsResult result) {
    return new MyPermissionsVO(
        RoleRefVO.fromAll(result.businessRoles()),
        result.applications().stream().map(MyPermissionsVO::application).toList());
  }

  /** 转换一个应用的已获权限。 */
  private static MyApplicationPermissions application(MyPermissionsResult.Application app) {
    return new MyApplicationPermissions(
        app.applicationId(),
        app.applicationName(),
        app.applicationCode(),
        app.menus().stream()
            .map(
                menu ->
                    new MyMenu(
                        menu.id(),
                        menu.name(),
                        menu.code(),
                        RoleRefVO.fromAll(menu.sourceRoles()),
                        menu.buttons().stream()
                            .map(
                                button ->
                                    new MyPermissionRef(
                                        button.id(),
                                        button.name(),
                                        button.code(),
                                        RoleRefVO.fromAll(button.sourceRoles())))
                            .toList()))
            .toList());
  }

  /**
   * 一个应用中的已获权限（MyApplicationPermissions）。
   *
   * @param applicationId 应用 ID
   * @param applicationName 应用名称
   * @param applicationCode 应用编码
   * @param menus 已获菜单
   */
  public record MyApplicationPermissions(
      long applicationId, String applicationName, String applicationCode, List<MyMenu> menus) {}

  /**
   * 已获菜单（MyMenu）。
   *
   * @param id 菜单权限 ID
   * @param name 菜单名称
   * @param code 菜单权限标识
   * @param sourceRoles 直接配置该菜单的角色
   * @param buttons 菜单下已获得的按钮
   */
  public record MyMenu(
      long id,
      String name,
      String code,
      List<RoleRefVO> sourceRoles,
      List<MyPermissionRef> buttons) {}

  /**
   * 已获按钮（MyPermissionRef）。
   *
   * @param id 按钮权限 ID
   * @param name 按钮名称
   * @param code 按钮权限标识
   * @param sourceRoles 直接配置该按钮的角色
   */
  public record MyPermissionRef(long id, String name, String code, List<RoleRefVO> sourceRoles) {}
}
