package com.rbac.rbac1ddd.application.role;

/**
 * 角色在某个业务应用下的有效权限数量。
 *
 * @param applicationId 应用 ID
 * @param applicationName 应用名称
 * @param permissionCount 该应用下的有效权限数
 */
public record RoleApplicationSummaryResult(
    long applicationId, String applicationName, long permissionCount) {}
