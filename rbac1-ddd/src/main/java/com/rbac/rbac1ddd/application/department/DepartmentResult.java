package com.rbac.rbac1ddd.application.department;

import com.rbac.rbac1ddd.domain.department.Department;

/**
 * 新增或修改部门后的结果（DepartmentView）。
 *
 * @param id 部门 ID
 * @param name 部门名称
 * @param parentId 上级部门 ID，顶级部门为 null
 */
public record DepartmentResult(long id, String name, Long parentId) {

  /** 由部门聚合逐字段转换。 */
  static DepartmentResult of(Department department) {
    return new DepartmentResult(department.getId(), department.getName(), department.getParentId());
  }
}
