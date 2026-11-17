package com.rbac.rbac1ddd.domain.user;

/** 用户的管理身份，由其持有的预置管理员角色决定。同时持有多个时取权限更大的一个。 */
public enum ManagementRole {
  /** 总管理员，可管理全部用户。 */
  TOTAL_ADMIN,
  /** 部门管理员，只能管理本部门及下级部门的用户。 */
  DEPARTMENT_ADMIN,
  /** 普通用户，没有管理身份。 */
  NONE
}
