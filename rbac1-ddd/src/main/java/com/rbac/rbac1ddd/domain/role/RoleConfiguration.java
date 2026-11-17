package com.rbac.rbac1ddd.domain.role;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * 值对象：一次提交的角色最终配置（名称、说明、直接权限、父角色）。
 *
 * @param name 角色名称
 * @param description 角色说明
 * @param permissionIds 直接权限 ID，构造时复制为按 ID 排序的集合
 * @param parentRoleIds 父角色 ID，构造时复制为按 ID 排序的集合
 */
public record RoleConfiguration(
    String name, String description, Set<Long> permissionIds, Set<Long> parentRoleIds) {

  /** 复制并排序集合，使差异比较和操作记录的输出顺序稳定。 */
  public RoleConfiguration {
    permissionIds = new TreeSet<>(permissionIds);
    parentRoleIds = new TreeSet<>(parentRoleIds);
  }

  /**
   * 从任意集合（例如请求中的列表）创建配置，重复 ID 只保留一个。
   *
   * @param name 角色名称
   * @param description 角色说明
   * @param permissionIds 直接权限 ID
   * @param parentIds 父角色 ID
   * @return 角色配置
   */
  public static RoleConfiguration of(
      String name, String description, Collection<Long> permissionIds, Collection<Long> parentIds) {
    return new RoleConfiguration(name, description, Set.copyOf(permissionIds), Set.copyOf(parentIds));
  }
}
