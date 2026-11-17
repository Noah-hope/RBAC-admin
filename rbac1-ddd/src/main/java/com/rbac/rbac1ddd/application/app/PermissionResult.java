package com.rbac.rbac1ddd.application.app;

import java.util.List;

/**
 * 权限资料和完整的关联接口（PermissionView）。
 *
 * @param id 权限 ID
 * @param applicationId 所属应用 ID
 * @param name 权限名称
 * @param code 权限标识
 * @param type 权限类型：MENU 或 BUTTON
 * @param parentId 按钮所属菜单 ID，菜单为 null
 * @param apis 关联的接口，按接口 ID 升序
 */
public record PermissionResult(
    long id,
    long applicationId,
    String name,
    String code,
    String type,
    Long parentId,
    List<ApiResult> apis) {}
