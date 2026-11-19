package com.rbac.rbac1.vo;

import com.rbac.rbac1.entity.Permission;

/**
 * 权限的摘要信息，用于权限目录、权限申请等响应。
 *
 * @param id 权限 ID
 * @param applicationId 所属应用 ID
 * @param name 权限名称
 * @param code 权限标识
 * @param type 权限类型：MENU 或 BUTTON
 * @param parentId 按钮所属菜单的 ID，菜单为 null
 */
public record PermissionSummaryVO(
    long id, long applicationId, String name, String code, String type, Long parentId) {

  /**
   * 由权限实体逐字段转换。
   *
   * @param p 权限实体
   * @return 权限摘要
   */
  public static PermissionSummaryVO of(Permission p) {
    return new PermissionSummaryVO(
        p.getId(), p.getApplicationId(), p.getName(), p.getCode(), p.getType(), p.getParentId());
  }
}
