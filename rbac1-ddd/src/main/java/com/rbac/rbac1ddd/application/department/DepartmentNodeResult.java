package com.rbac.rbac1ddd.application.department;

import java.util.List;

/**
 * 部门树中的一个节点（DepartmentNode）。
 *
 * @param id 部门 ID
 * @param name 部门名称
 * @param parentId 上级部门 ID，顶级部门为 null
 * @param children 直接下级部门，按 ID 升序
 */
public record DepartmentNodeResult(
    long id, String name, Long parentId, List<DepartmentNodeResult> children) {}
