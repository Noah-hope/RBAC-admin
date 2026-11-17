package com.rbac.rbac1ddd.application.request;

import java.time.Instant;
import java.util.List;

/**
 * 一条权限申请（PermissionRequestView）。
 *
 * @param id 申请 ID
 * @param applicantUserId 申请人用户 ID
 * @param applicantName 申请人姓名
 * @param applicantDepartmentId 申请人当前所属部门 ID
 * @param permission 所申请的权限
 * @param reason 申请理由
 * @param status 申请状态名称
 * @param reviewerUserId 审核人用户 ID
 * @param approvedRoleId 审批通过时分配的业务角色 ID
 * @param createdAt 提交时间
 * @param reviewedAt 审核时间
 * @param approvalRoles 审批候选角色，只在有权审核者查看详情时提供，其他情况为 null
 */
public record PermissionRequestResult(
    long id,
    long applicantUserId,
    String applicantName,
    Long applicantDepartmentId,
    PermissionSummaryResult permission,
    String reason,
    String status,
    Long reviewerUserId,
    Long approvedRoleId,
    Instant createdAt,
    Instant reviewedAt,
    List<ApprovalOptionResult> approvalRoles) {}
