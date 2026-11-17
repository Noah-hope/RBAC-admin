package com.rbac.rbac1ddd.domain.user;

import java.util.Set;
import java.util.TreeSet;

/**
 * 值对象：一次用户角色变更中新增和解除的角色。
 *
 * @param added 新增的角色 ID，构造时复制为按 ID 排序的集合
 * @param removed 解除的角色 ID，构造时复制为按 ID 排序的集合
 */
public record RoleAssignmentChange(Set<Long> added, Set<Long> removed) {

  /** 复制并排序集合，使操作记录的输出顺序稳定。 */
  public RoleAssignmentChange {
    added = new TreeSet<>(added);
    removed = new TreeSet<>(removed);
  }

  /**
   * 判断是否没有任何实际变化，没有变化时不需要保存和记录。
   *
   * @return 新增和解除都为空时为 true
   */
  public boolean isEmpty() {
    return added.isEmpty() && removed.isEmpty();
  }
}
