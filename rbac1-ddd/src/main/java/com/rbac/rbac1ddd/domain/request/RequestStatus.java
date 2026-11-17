package com.rbac.rbac1ddd.domain.request;

/** 权限申请状态。只有待审核状态会被授权变化自动结算。 */
public enum RequestStatus {
  /** 待审核。 */
  PENDING,
  /** 审核通过。 */
  APPROVED,
  /** 审核拒绝。 */
  REJECTED,
  /** 已满足：申请人通过其他授权变化已经拥有所申请权限，无需再审核。 */
  SATISFIED,
  /** 申请人主动撤回。 */
  WITHDRAWN
}
