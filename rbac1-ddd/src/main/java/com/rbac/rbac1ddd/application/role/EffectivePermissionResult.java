package com.rbac.rbac1ddd.application.role;

import java.util.List;

/**
 * 角色的一条有效权限及其获得方式。
 *
 * @param id 权限 ID
 * @param applicationId 所属应用 ID
 * @param name 权限名称
 * @param code 权限编码
 * @param type 权限类型名称
 * @param parentId 上级权限 ID，可能为空
 * @param acquisition 获得方式名称：DIRECT、INHERITED 或 BOTH
 * @param sourceRoles 提供该权限的角色
 */
public record EffectivePermissionResult(
    long id,
    long applicationId,
    String name,
    String code,
    String type,
    Long parentId,
    String acquisition,
    List<RoleRefResult> sourceRoles) {}
