package com.rbac.rbac1.vo;

/**
 * 被引用的角色。
 *
 * @param id 角色 ID
 * @param name 角色名称
 */
public record RoleRefVO(long id, String name) {}
