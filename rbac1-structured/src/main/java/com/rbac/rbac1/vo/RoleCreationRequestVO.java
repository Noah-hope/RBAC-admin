package com.rbac.rbac1.vo;

import java.time.Instant;
import java.util.List;

/**
 * 角色创建申请的响应体。
 *
 * @param id 申请 ID
 * @param applicantUserId 申请人 ID
 * @param applicantName 申请人姓名
 * @param name 申请的角色名称
 * @param description 申请的角色说明
 * @param permissionIds 申请的直接权限 ID
 * @param inheritedRoles 申请继承的父角色
 * @param status 申请状态
 * @param reviewerUserId 审核人 ID
 * @param createdRoleId 通过后创建的角色 ID
 * @param createdAt 提交时间
 * @param reviewedAt 审核时间
 */
public record RoleCreationRequestVO(
    long id,
    long applicantUserId,
    String applicantName,
    String name,
    String description,
    List<Long> permissionIds,
    List<RequestInheritedRoleVO> inheritedRoles,
    String status,
    Long reviewerUserId,
    Long createdRoleId,
    Instant createdAt,
    Instant reviewedAt) {}
