package com.rbac.rbac1ddd.application.user;

/** 批量角色操作的类型。 */
public enum BatchAction {
  /** 为所选用户分配角色。 */
  GRANT,
  /** 为所选用户撤销角色。 */
  REVOKE
}
