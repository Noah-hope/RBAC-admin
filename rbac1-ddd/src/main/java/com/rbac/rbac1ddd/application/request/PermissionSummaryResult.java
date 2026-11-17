package com.rbac.rbac1ddd.application.request;

import com.rbac.rbac1ddd.domain.permission.Permission;

/**
 * 权限摘要（PermissionSummary），用于申请目录和申请记录。
 *
 * @param id 权限 ID
 * @param applicationId 所属应用 ID
 * @param name 权限名称
 * @param code 权限标识
 * @param type 权限类型名称：MENU 或 BUTTON
 * @param parentId 按钮所属菜单 ID，菜单为 null
 */
public record PermissionSummaryResult(
    long id, long applicationId, String name, String code, String type, Long parentId) {

  /** 由权限目录中的权限转换。 */
  static PermissionSummaryResult of(Permission permission) {
    return new PermissionSummaryResult(
        permission.id(),
        permission.applicationId(),
        permission.name(),
        permission.code(),
        permission.type().name(),
        permission.parentId());
  }
}
