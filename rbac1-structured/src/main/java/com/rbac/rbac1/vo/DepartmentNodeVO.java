package com.rbac.rbac1.vo;

import java.util.List;

/**
 * 部门树中的一个节点。
 *
 * @param id 部门 ID
 * @param name 部门名称
 * @param parentId 上级部门 ID，顶级部门为 null
 * @param children 下级部门
 */
public record DepartmentNodeVO(long id, String name, Long parentId, List<DepartmentNodeVO> children) {}
