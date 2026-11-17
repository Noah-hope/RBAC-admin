package com.rbac.rbac1ddd.application.request;

import java.time.Instant;
import java.util.List;

/**
 * 一条角色创建申请（RoleCreationView）。
 *
 * @param id 申请 ID
 * @param applicantUserId 申请人用户 ID
 * @param applicantName 申请人姓名
 * @param name 申请的角色名称
 * @param description 申请的角色说明
 * @param permissionIds 申请的直接权限 ID，升序
 * @param inheritedRoles 所选父角色快照及其当前是否存在，按 ID 升序
 * @param status 申请状态名称
 * @param reviewerUserId 审核人用户 ID
 * @param createdRoleId 审批通过后实际创建的角色 ID
 * @param createdAt 提交时间
 * @param reviewedAt 审核时间
 */
public record RoleCreationRequestResult(
    long id,
    long applicantUserId,
    String applicantName,
    String name,
    String description,
    List<Long> permissionIds,
    List<RoleCreationRequestResult.InheritedRole> inheritedRoles,
    String status,
    Long reviewerUserId,
    Long createdRoleId,
    Instant createdAt,
    Instant reviewedAt) {

  /**
   * 父角色快照（RequestInheritedRole）。
   *
   * @param id 父角色 ID
   * @param name 提交申请时的父角色名称
   * @param exists 父角色当前是否存在
   */
  public record InheritedRole(long id, String name, boolean exists) {}
}
