package com.rbac.rbac1.vo;

/**
 * 部门的响应体。
 *
 * @param id 部门 ID
 * @param name 部门名称
 * @param parentId 上级部门 ID，顶级部门为 null
 */
public record DepartmentVO(long id, String name, Long parentId) {}
