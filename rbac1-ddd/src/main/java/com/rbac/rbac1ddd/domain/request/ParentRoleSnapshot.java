package com.rbac.rbac1ddd.domain.request;

/**
 * 值对象：角色创建申请提交时所选父角色的 ID 和名称快照。
 *
 * <p>父角色之后改名或被删除，申请历史仍显示提交时的名称；当前是否存在由继承图判断。
 *
 * @param roleId 父角色 ID
 * @param name 提交时的父角色名称
 */
public record ParentRoleSnapshot(long roleId, String name) {}
