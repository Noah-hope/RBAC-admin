package com.rbac.rbac1.vo;

/**
 * 角色创建申请中申请继承的父角色。
 *
 * @param id 父角色 ID
 * @param name 父角色名称（申请时的名称）
 * @param exists 父角色当前是否仍存在
 */
public record RequestInheritedRoleVO(long id, String name, boolean exists) {}
