package com.rbac.rbac1ddd.domain.role;

/** 有效权限的获得方式：直接配置、继承获得，或两者都有。 */
public enum Acquisition {
  /** 只由角色自身直接配置。 */
  DIRECT,
  /** 只从父角色（含多层祖先）继承获得。 */
  INHERITED,
  /** 既直接配置又从父角色继承，解除任一来源后仍然保留该权限。 */
  BOTH
}
