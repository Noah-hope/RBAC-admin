package com.rbac.rbac1ddd.domain.role;

/** 角色类型。只有业务角色可以修改、删除和参与继承。 */
public enum RoleType {
  /** 业务角色：可新建、修改、删除、继承，并分配给用户。 */
  BUSINESS,
  /** 预置的总管理员角色，只读，可管理全部用户。 */
  TOTAL_ADMIN,
  /** 预置的部门管理员角色，只读，只能管理本部门及下级部门的用户。 */
  DEPARTMENT_ADMIN
}
