package com.rbac.rbac1.vo;

import java.util.List;

/**
 * 角色列表中的一项。
 *
 * @param id 角色 ID
 * @param name 角色名称
 * @param description 角色说明
 * @param applications 各应用的权限数量
 * @param roleType 角色类型
 * @param inheritedRoles 直接父角色
 * @param inheritedByRoles 直接子角色
 * @param directPermissionCount 直接权限数
 * @param effectivePermissionCount 有效权限数
 */
public record RoleSummaryVO(
    long id,
    String name,
    String description,
    List<RoleApplicationSummaryVO> applications,
    String roleType,
    List<RoleRefVO> inheritedRoles,
    List<RoleRefVO> inheritedByRoles,
    int directPermissionCount,
    int effectivePermissionCount) {}
