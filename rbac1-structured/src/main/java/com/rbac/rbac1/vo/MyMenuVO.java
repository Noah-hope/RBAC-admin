package com.rbac.rbac1.vo;

import java.util.List;

/**
 * 当前用户拥有的菜单权限及其按钮。
 *
 * @param id 菜单权限 ID
 * @param name 菜单名称
 * @param code 菜单权限标识
 * @param sourceRoles 授予该菜单的角色
 * @param buttons 拥有的按钮权限
 */
public record MyMenuVO(
    long id,
    String name,
    String code,
    List<RoleRefVO> sourceRoles,
    List<MyPermissionRefVO> buttons) {}
