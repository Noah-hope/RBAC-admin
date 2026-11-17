package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.user.DepartmentRefResult;

/**
 * 用户所属部门的引用（DepartmentRef）。
 *
 * @param id 部门 ID
 * @param name 部门名称
 */
public record DepartmentRefVO(long id, String name) {

  /** 由应用层结果逐字段转换。 */
  public static DepartmentRefVO from(DepartmentRefResult result) {
    return new DepartmentRefVO(result.id(), result.name());
  }
}
