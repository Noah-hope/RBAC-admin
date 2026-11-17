package com.rbac.rbac1ddd.application.request;

import com.rbac.rbac1ddd.application.role.RoleDetailsResult;
import java.util.List;

/**
 * 审批权限申请时的一个候选角色（ApprovalOption）。
 *
 * @param role 候选角色详情，包括直接权限、继承权限和实际配置来源
 * @param selectable 没有附带无关权限、可以选择时为 true
 * @param extraPermissionIds 附带的无关权限 ID，升序
 */
public record ApprovalOptionResult(
    RoleDetailsResult role, boolean selectable, List<Long> extraPermissionIds) {}
