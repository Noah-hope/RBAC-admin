package com.rbac.rbac1ddd.domain.role;

import java.util.Objects;
import java.util.Set;

/**
 * 值对象：一次角色修改前后的差异，供操作记录使用。
 *
 * @param oldName 修改前名称
 * @param newName 修改后名称
 * @param oldDescription 修改前说明
 * @param newDescription 修改后说明
 * @param oldPermissionIds 修改前的直接权限 ID
 * @param newPermissionIds 修改后的直接权限 ID
 * @param oldParentRoleIds 修改前的父角色 ID
 * @param newParentRoleIds 修改后的父角色 ID
 */
public record RoleChange(
    String oldName,
    String newName,
    String oldDescription,
    String newDescription,
    Set<Long> oldPermissionIds,
    Set<Long> newPermissionIds,
    Set<Long> oldParentRoleIds,
    Set<Long> newParentRoleIds) {

  /**
   * 判断名称是否变化。
   *
   * @return 名称不同时为 true
   */
  public boolean nameChanged() {
    return !Objects.equals(oldName, newName);
  }

  /**
   * 判断说明是否变化。说明可以为 null，因此用 {@link Objects#equals} 比较。
   *
   * @return 说明不同时为 true
   */
  public boolean descriptionChanged() {
    return !Objects.equals(oldDescription, newDescription);
  }

  /**
   * 判断直接权限是否变化，按集合内容比较，与顺序无关。
   *
   * @return 直接权限集合不同时为 true
   */
  public boolean permissionsChanged() {
    return !oldPermissionIds.equals(newPermissionIds);
  }

  /**
   * 判断父角色是否变化，按集合内容比较，与顺序无关。
   *
   * @return 父角色集合不同时为 true
   */
  public boolean parentsChanged() {
    return !oldParentRoleIds.equals(newParentRoleIds);
  }

  /**
   * 判断本次提交是否没有任何实际修改，没有修改时不需要写操作记录。
   *
   * @return 四类内容都未变化时为 true
   */
  public boolean isEmpty() {
    return !nameChanged() && !descriptionChanged() && !permissionsChanged() && !parentsChanged();
  }
}
