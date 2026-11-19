package com.rbac.rbac1.vo;

import java.util.List;

/**
 * 角色或申请配置的一条有效权限及其来源。
 *
 * @param id 权限 ID
 * @param applicationId 所属应用 ID
 * @param name 权限名称
 * @param code 权限标识
 * @param type 权限类型：MENU 或 BUTTON
 * @param parentId 按钮所属菜单的 ID，菜单为 null
 * @param acquisition 获得方式：DIRECT、INHERITED 或 BOTH
 * @param sourceRoles 提供该权限的角色
 */
public record EffectivePermissionVO(
    long id,
    long applicationId,
    String name,
    String code,
    String type,
    Long parentId,
    String acquisition,
    List<RoleRefVO> sourceRoles) {}
