package com.rbac.rbac1ddd.application.app;

import com.rbac.rbac1ddd.domain.permission.PermissionType;
import java.util.List;

/**
 * 登记菜单或按钮权限的命令。
 *
 * @param name 权限名称
 * @param code 权限标识
 * @param type 菜单或按钮
 * @param parentId 按钮所属菜单 ID，菜单为 null
 * @param apiIds 关联的接口 ID，没有关联时为空列表
 */
public record PermissionCreateCommand(
    String name, String code, PermissionType type, Long parentId, List<Long> apiIds) {}
