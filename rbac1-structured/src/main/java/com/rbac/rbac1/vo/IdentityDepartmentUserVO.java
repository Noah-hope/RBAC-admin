package com.rbac.rbac1.vo;

/** 部门选人结果，含用户所属部门。 */
public record IdentityDepartmentUserVO(
    String userId, String no, String name, long departmentId, String departmentName) {}
