package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.auth.IdentityDirectoryReader.DepartmentResult;

/** 供业务系统逐层选择的部门。 */
public record IdentityDepartmentVO(long id, String code, String name, Long parentId, boolean hasChildren) {
  /** 转换目录结果，统一身份 ID 使用与 JWT sub 一致的字符串。 */
  public static IdentityDepartmentVO from(DepartmentResult row) {
    return new IdentityDepartmentVO(row.id(), Long.toString(row.id()), row.name(), row.parentId(), row.hasChildren());
  }
}
