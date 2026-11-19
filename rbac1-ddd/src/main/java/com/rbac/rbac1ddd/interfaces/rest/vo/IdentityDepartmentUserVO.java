package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.auth.IdentityDirectoryReader.DepartmentUserResult;

/** 部门选人结果，含用户所属部门。 */
public record IdentityDepartmentUserVO(
    String userId, String no, String name, long departmentId, String departmentName) {
  /** 转换目录结果，统一身份 ID 使用与 JWT sub 一致的字符串。 */
  public static IdentityDepartmentUserVO from(DepartmentUserResult row) {
    return new IdentityDepartmentUserVO(
        Long.toString(row.userId()), row.no(), row.name(), row.departmentId(), row.departmentName());
  }
}
