package com.rbac.rbac1.vo;

/** 供业务系统逐层选择的部门。 */
public record IdentityDepartmentVO(long id, String code, String name, Long parentId, boolean hasChildren) {}
