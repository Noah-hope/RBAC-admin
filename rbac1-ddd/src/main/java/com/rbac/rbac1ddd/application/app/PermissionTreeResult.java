package com.rbac.rbac1ddd.application.app;

import java.util.List;

/**
 * 应用的完整权限树（ApplicationPermissions）：菜单在第一层，按钮挂在所属菜单下。
 *
 * @param applicationId 应用 ID
 * @param applicationName 应用名称
 * @param menus 菜单节点，按 ID 升序
 */
public record PermissionTreeResult(
    long applicationId, String applicationName, List<PermissionTreeResult.Menu> menus) {

  /**
   * 菜单节点（MenuNode）。
   *
   * @param id 菜单权限 ID
   * @param name 菜单名称
   * @param code 菜单权限标识
   * @param buttons 菜单下的按钮，按 ID 升序
   */
  public record Menu(long id, String name, String code, List<Button> buttons) {}

  /**
   * 按钮节点（PermissionRef）。
   *
   * @param id 按钮权限 ID
   * @param name 按钮名称
   * @param code 按钮权限标识
   */
  public record Button(long id, String name, String code) {}
}
