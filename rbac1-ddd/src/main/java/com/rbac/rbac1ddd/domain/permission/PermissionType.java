package com.rbac.rbac1ddd.domain.permission;

/** 权限类型：菜单没有上级，按钮必须挂在同一应用的菜单下。 */
public enum PermissionType {
  /** 菜单权限，没有上级权限。 */
  MENU,
  /** 按钮权限，必须属于同一应用的某个菜单，授予时需同时拥有该菜单。 */
  BUTTON
}
