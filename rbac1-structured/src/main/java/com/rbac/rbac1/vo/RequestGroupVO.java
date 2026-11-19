package com.rbac.rbac1.vo;

import java.util.List;

/**
 * 可申请权限中的一个菜单分组。
 *
 * @param menu 菜单权限
 * @param permissions 该菜单下可申请的权限
 */
public record RequestGroupVO(PermissionSummaryVO menu, List<PermissionSummaryVO> permissions) {}
