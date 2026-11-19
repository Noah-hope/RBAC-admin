package com.rbac.rbac1.vo;

/**
 * 应用权限树中的按钮权限。
 *
 * @param id 权限 ID
 * @param name 权限名称
 * @param code 权限标识
 */
public record PermissionRefVO(long id, String name, String code) {}
