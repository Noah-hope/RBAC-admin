package com.rbac.rbac1ddd.domain.user;

/** 账号状态。停用账号保留角色关联，但不拥有任何有效权限。 */
public enum UserStatus {
  /** 正常启用。 */
  ACTIVE,
  /** 已停用。 */
  DISABLED
}
