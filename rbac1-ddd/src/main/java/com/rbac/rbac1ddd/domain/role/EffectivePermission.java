package com.rbac.rbac1ddd.domain.role;

import java.util.Set;

/**
 * 值对象：角色的一项有效权限。
 *
 * @param permissionId 权限 ID
 * @param acquisition 获得方式：直接、继承或两者都有
 * @param sourceRoleIds 直接拥有该权限的角色（自身或祖先），有多个来源时全部列出
 */
public record EffectivePermission(
    long permissionId, Acquisition acquisition, Set<Long> sourceRoleIds) {

  /** 复制来源集合，保证值对象不可变。 */
  public EffectivePermission {
    sourceRoleIds = Set.copyOf(sourceRoleIds);
  }
}
