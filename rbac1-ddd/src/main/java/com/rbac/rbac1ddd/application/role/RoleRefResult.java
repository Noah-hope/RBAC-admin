package com.rbac.rbac1ddd.application.role;

import com.rbac.rbac1ddd.domain.role.RoleHierarchy;
import java.util.Collection;
import java.util.List;

/**
 * 角色引用，用于列出继承关系和权限来源。
 *
 * @param id 角色 ID
 * @param name 角色名称
 */
public record RoleRefResult(long id, String name) {

  /**
   * 把角色 ID 转换为按 ID 升序排列的角色引用，名称取自当前继承图。
   *
   * @param hierarchy 当前角色继承图
   * @param ids 已存在的角色 ID
   * @return 按 ID 升序排列的角色引用
   */
  public static List<RoleRefResult> listOf(RoleHierarchy hierarchy, Collection<Long> ids) {
    return ids.stream().sorted().map(id -> new RoleRefResult(id, hierarchy.nameOf(id))).toList();
  }
}
