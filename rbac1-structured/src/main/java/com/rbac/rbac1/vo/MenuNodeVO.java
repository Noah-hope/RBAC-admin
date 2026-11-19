package com.rbac.rbac1.vo;

import java.util.List;

/**
 * 应用权限树中的菜单及其按钮。
 *
 * @param id 菜单权限 ID
 * @param name 菜单名称
 * @param code 菜单权限标识
 * @param buttons 菜单下的按钮权限
 */
public record MenuNodeVO(long id, String name, String code, List<PermissionRefVO> buttons) {}
