package com.rbac.rbac1.vo;

/**
 * 前端权限检查的响应体。
 *
 * @param allowed 拥有该权限时为 true
 */
public record PermissionCheckVO(boolean allowed) {}
