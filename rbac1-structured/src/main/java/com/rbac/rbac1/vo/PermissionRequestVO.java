package com.rbac.rbac1.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * 权限申请的响应体。
 *
 * @param id 申请 ID
 * @param applicantUserId 申请人 ID
 * @param applicantName 申请人姓名
 * @param applicantDepartmentId 申请人部门 ID
 * @param permission 申请的权限
 * @param reason 申请理由
 * @param status 申请状态
 * @param reviewerUserId 审核人 ID
 * @param approvedRoleId 通过时分配的业务角色 ID
 * @param createdAt 提交时间
 * @param reviewedAt 审核时间
 * @param approvalRoles 可选的审核角色，只在审核人查看待审核申请时返回
 */
public record PermissionRequestVO(
    long id,
    long applicantUserId,
    String applicantName,
    Long applicantDepartmentId,
    PermissionSummaryVO permission,
    String reason,
    String status,
    Long reviewerUserId,
    Long approvedRoleId,
    Instant createdAt,
    Instant reviewedAt,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<ApprovalOptionVO> approvalRoles) {}
