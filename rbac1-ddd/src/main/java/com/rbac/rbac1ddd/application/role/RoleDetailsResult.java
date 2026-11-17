package com.rbac.rbac1ddd.application.role;

import java.util.List;

/**
 * 角色详情。
 *
 * @param id 角色 ID
 * @param name 角色名称
 * @param description 角色说明
 * @param applications 按应用汇总的有效权限数
 * @param roleType 角色类型名称
 * @param inheritedRoles 直接继承的父角色
 * @param inheritedByRoles 直接继承本角色的子角色
 * @param directPermissionCount 直接权限数
 * @param effectivePermissionCount 有效权限数（含继承）
 * @param permissionIds 直接权限 ID，升序
 * @param effectivePermissions 有效权限明细
 * @param assignedUserCount 关联该角色的用户数
 */
public record RoleDetailsResult(
    long id,
    String name,
    String description,
    List<RoleApplicationSummaryResult> applications,
    String roleType,
    List<RoleRefResult> inheritedRoles,
    List<RoleRefResult> inheritedByRoles,
    int directPermissionCount,
    int effectivePermissionCount,
    List<Long> permissionIds,
    List<EffectivePermissionResult> effectivePermissions,
    long assignedUserCount) {}
