package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.request.PermissionSummaryResult;

/**
 * 权限摘要（PermissionSummary），字段与 rbac1-structured 相同。
 *
 * @param id 权限 ID
 * @param applicationId 所属应用 ID
 * @param name 权限名称
 * @param code 权限标识
 * @param type 权限类型：MENU 或 BUTTON
 * @param parentId 按钮所属菜单 ID，菜单为 null
 */
public record PermissionSummaryVO(
    long id, long applicationId, String name, String code, String type, Long parentId) {

  /** 由应用层结果逐字段转换。 */
  public static PermissionSummaryVO from(PermissionSummaryResult result) {
    return new PermissionSummaryVO(
        result.id(),
        result.applicationId(),
        result.name(),
        result.code(),
        result.type(),
        result.parentId());
  }
}
