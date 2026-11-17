package com.rbac.rbac1ddd.domain.request;

/** 管理员对申请的审核结论，权限申请和角色创建申请共用。 */
public enum ReviewDecision {
  /** 通过。 */
  APPROVE,
  /** 拒绝。 */
  REJECT
}
