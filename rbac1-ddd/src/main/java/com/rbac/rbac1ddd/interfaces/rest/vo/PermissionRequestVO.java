package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rbac.rbac1ddd.application.request.PermissionRequestResult;
import java.time.Instant;
import java.util.List;

/**
 * 一条权限申请（PermissionRequest），字段与 rbac1-structured 的 PermissionRequestView 相同。
 *
 * @param id 申请 ID
 * @param applicantUserId 申请人用户 ID
 * @param applicantName 申请人姓名
 * @param applicantDepartmentId 申请人当前所属部门 ID
 * @param permission 所申请的权限
 * @param reason 申请理由
 * @param status 申请状态
 * @param reviewerUserId 审核人用户 ID
 * @param approvedRoleId 审批通过时分配的业务角色 ID
 * @param createdAt 提交时间
 * @param reviewedAt 审核时间
 * @param approvalRoles 审批候选角色，只有有权审核者查看详情时出现
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
    List<ApprovalOptionVO> approvalRoles) {

  /** 由应用层结果逐字段转换。 */
  public static PermissionRequestVO from(PermissionRequestResult result) {
    return new PermissionRequestVO(
        result.id(),
        result.applicantUserId(),
        result.applicantName(),
        result.applicantDepartmentId(),
        PermissionSummaryVO.from(result.permission()),
        result.reason(),
        result.status(),
        result.reviewerUserId(),
        result.approvedRoleId(),
        result.createdAt(),
        result.reviewedAt(),
        result.approvalRoles() == null
            ? null
            : result.approvalRoles().stream().map(ApprovalOptionVO::from).toList());
  }
}
