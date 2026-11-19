package com.rbac.rbac1.vo;

import java.util.List;

/**
 * 审核权限申请时可选的业务角色。
 *
 * @param role 角色详情
 * @param selectable 该角色包含所申请的权限时为 true
 * @param extraPermissionIds 选择该角色会额外获得的权限 ID
 */
public record ApprovalOptionVO(
    RoleDetailsVO role, boolean selectable, List<Long> extraPermissionIds) {}
