package com.rbac.rbac1.vo;

import java.util.List;

/**
 * 当前用户拥有的按钮权限。
 *
 * @param id 权限 ID
 * @param name 权限名称
 * @param code 权限标识
 * @param sourceRoles 授予该权限的角色
 */
public record MyPermissionRefVO(long id, String name, String code, List<RoleRefVO> sourceRoles) {}
