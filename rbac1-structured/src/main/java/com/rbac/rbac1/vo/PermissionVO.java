package com.rbac.rbac1.vo;

import java.util.List;

/**
 * 权限详情的响应体，含关联的接口。
 *
 * @param id 权限 ID
 * @param applicationId 所属应用 ID
 * @param name 权限名称
 * @param code 权限标识
 * @param type 权限类型：MENU 或 BUTTON
 * @param parentId 按钮所属菜单的 ID，菜单为 null
 * @param apis 关联的接口
 */
public record PermissionVO(
    long id,
    long applicationId,
    String name,
    String code,
    String type,
    Long parentId,
    List<ApiVO> apis) {}
