package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.department.DepartmentResult;

/**
 * 新增或修改部门后的响应体（DepartmentView）。
 *
 * @param id 部门 ID
 * @param name 部门名称
 * @param parentId 上级部门 ID，顶级部门为 null
 */
public record DepartmentVO(long id, String name, Long parentId) {

  /** 由应用层结果逐字段转换。 */
  public static DepartmentVO from(DepartmentResult result) {
    return new DepartmentVO(result.id(), result.name(), result.parentId());
  }
}
