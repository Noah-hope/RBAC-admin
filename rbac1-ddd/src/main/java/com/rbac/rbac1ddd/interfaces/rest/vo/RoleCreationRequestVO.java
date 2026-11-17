package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.request.RoleCreationRequestResult;
import java.time.Instant;
import java.util.List;

/**
 * 一条角色创建申请（RoleCreationRequest），字段与 rbac1-structured 的 RoleCreationView 相同。
 *
 * @param id 申请 ID
 * @param applicantUserId 申请人用户 ID
 * @param applicantName 申请人姓名
 * @param name 申请的角色名称
 * @param description 申请的角色说明
 * @param permissionIds 申请的直接权限 ID
 * @param inheritedRoles 父角色快照
 * @param status 申请状态
 * @param reviewerUserId 审核人用户 ID
 * @param createdRoleId 实际创建的角色 ID
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
    List<RoleCreationRequestVO.InheritedRole> inheritedRoles,
    String status,
    Long reviewerUserId,
    Long createdRoleId,
    Instant createdAt,
    Instant reviewedAt) {

  /** 由应用层结果逐字段转换。 */
  public static RoleCreationRequestVO from(RoleCreationRequestResult result) {
    return new RoleCreationRequestVO(
        result.id(),
        result.applicantUserId(),
        result.applicantName(),
        result.name(),
        result.description(),
        result.permissionIds(),
        result.inheritedRoles().stream()
            .map(parent -> new InheritedRole(parent.id(), parent.name(), parent.exists()))
            .toList(),
        result.status(),
        result.reviewerUserId(),
        result.createdRoleId(),
        result.createdAt(),
        result.reviewedAt());
  }

  /**
   * 父角色快照（RequestInheritedRole）。
   *
   * @param id 父角色 ID
   * @param name 提交申请时的父角色名称
   * @param exists 父角色当前是否存在
   */
  public record InheritedRole(long id, String name, boolean exists) {}
}
