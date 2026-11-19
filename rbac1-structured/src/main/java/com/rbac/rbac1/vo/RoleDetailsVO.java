package com.rbac.rbac1.vo;

import java.util.List;

/**
 * 角色详情的响应体。
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
 * @param permissionIds 直接权限 ID
 * @param effectivePermissions 有效权限及其来源
 * @param assignedUserCount 直接分配该角色的用户数
 */
public record RoleDetailsVO(
    long id,
    String name,
    String description,
    List<RoleApplicationSummaryVO> applications,
    String roleType,
    List<RoleRefVO> inheritedRoles,
    List<RoleRefVO> inheritedByRoles,
    int directPermissionCount,
    int effectivePermissionCount,
    List<Long> permissionIds,
    List<EffectivePermissionVO> effectivePermissions,
    long assignedUserCount) {}
