package com.rbac.rbac1ddd.domain.permission;

/**
 * 菜单或按钮权限。在角色上下文中只读，由权限目录统一提供。
 *
 * @param id 权限 ID
 * @param applicationId 所属应用 ID
 * @param parentId 按钮所属菜单的 ID；菜单没有上级，为 null
 * @param name 权限名称，用于错误提示
 * @param code 权限编码
 * @param type 权限类型：菜单或按钮
 */
public record Permission(
    long id, long applicationId, Long parentId, String name, String code, PermissionType type) {

  /**
   * 判断是否为按钮权限。按钮必须和所属菜单一起授予，校验时需要区分。
   *
   * @return 按钮权限时为 true
   */
  public boolean isButton() {
    return type == PermissionType.BUTTON;
  }
}
