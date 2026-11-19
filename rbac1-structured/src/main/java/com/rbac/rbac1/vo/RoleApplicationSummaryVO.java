package com.rbac.rbac1.vo;

/**
 * 角色在一个应用内的权限数量。
 *
 * @param applicationId 应用 ID
 * @param applicationName 应用名称
 * @param permissionCount 该应用内的有效权限数
 */
public record RoleApplicationSummaryVO(
    long applicationId, String applicationName, long permissionCount) {}
