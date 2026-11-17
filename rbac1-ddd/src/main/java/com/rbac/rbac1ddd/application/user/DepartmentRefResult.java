package com.rbac.rbac1ddd.application.user;

/**
 * 用户所属部门的引用（DepartmentRef）。
 *
 * @param id 部门 ID
 * @param name 部门名称
 */
public record DepartmentRefResult(long id, String name) {}
