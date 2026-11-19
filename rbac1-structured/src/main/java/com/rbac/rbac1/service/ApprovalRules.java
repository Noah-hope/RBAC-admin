package com.rbac.rbac1.service;

import java.util.List;
import java.util.Set;

/**
 * 权限申请审核的纯计算规则，不依赖数据库，供审核选项和审核通过两处复用。
 */
public final class ApprovalRules {

  private ApprovalRules() {}

  /**
   * 计算批准某个角色时申请人会额外获得的权限。
   *
   * <p>被申请的权限本身、它的父菜单以及申请人已经拥有的权限都不算「额外」，结果按 ID 升序返回，
   * 让审核人看到稳定的顺序。
   *
   * @param selected 候选角色的全部有效权限 ID
   * @param owned 申请人当前已拥有的权限 ID
   * @param requested 被申请的权限 ID
   * @param parent 被申请权限的父菜单 ID，菜单权限本身为 {@code null}
   * @return 额外获得的权限 ID，升序
   */
  public static List<Long> extraPermissions(
      Set<Long> selected, Set<Long> owned, long requested, Long parent) {
    return selected.stream()
        .filter(id -> id != requested && !id.equals(parent) && !owned.contains(id))
        .sorted()
        .toList();
  }
}
