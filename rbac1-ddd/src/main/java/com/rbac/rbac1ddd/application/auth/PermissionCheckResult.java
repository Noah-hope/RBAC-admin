package com.rbac.rbac1ddd.application.auth;

/**
 * 菜单或按钮权限检查的结果。
 *
 * @param allowed 当前用户在目标应用中拥有该权限时为 true
 */
public record PermissionCheckResult(boolean allowed) {}
