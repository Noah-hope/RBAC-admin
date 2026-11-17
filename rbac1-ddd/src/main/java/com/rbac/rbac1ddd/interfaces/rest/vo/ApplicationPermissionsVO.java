package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.app.PermissionTreeResult;
import java.util.List;

/**
 * 应用权限树的响应体（ApplicationPermissions）。
 *
 * @param applicationId 应用 ID
 * @param applicationName 应用名称
 * @param menus 菜单节点
 */
public record ApplicationPermissionsVO(
    long applicationId, String applicationName, List<ApplicationPermissionsVO.MenuNode> menus) {

  /** 由应用层结果转换，菜单和按钮逐个转换。 */
  public static ApplicationPermissionsVO from(PermissionTreeResult result) {
    return new ApplicationPermissionsVO(
        result.applicationId(),
        result.applicationName(),
        result.menus().stream()
            .map(
                menu ->
                    new MenuNode(
                        menu.id(),
                        menu.name(),
                        menu.code(),
                        menu.buttons().stream()
                            .map(button -> new PermissionRef(button.id(), button.name(), button.code()))
                            .toList()))
            .toList());
  }

  /**
   * 菜单节点（MenuNode）。
   *
   * @param id 菜单权限 ID
   * @param name 菜单名称
   * @param code 菜单权限标识
   * @param buttons 菜单下的按钮
   */
  public record MenuNode(long id, String name, String code, List<PermissionRef> buttons) {}

  /**
   * 按钮节点（PermissionRef）。
   *
   * @param id 按钮权限 ID
   * @param name 按钮名称
   * @param code 按钮权限标识
   */
  public record PermissionRef(long id, String name, String code) {}
}
