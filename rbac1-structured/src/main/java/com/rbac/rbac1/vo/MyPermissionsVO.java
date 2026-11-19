package com.rbac.rbac1.vo;

import java.util.List;

/**
 * 当前用户的业务角色和按应用分组的有效权限。
 *
 * @param businessRoles 直接分配的业务角色
 * @param applications 各应用的菜单—按钮权限
 */
public record MyPermissionsVO(
    List<RoleRefVO> businessRoles, List<MyApplicationPermissionsVO> applications) {}
