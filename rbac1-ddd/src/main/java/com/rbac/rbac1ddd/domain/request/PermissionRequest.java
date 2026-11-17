package com.rbac.rbac1ddd.domain.request;

import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.CONFLICT;
import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.NOT_FOUND;

import com.rbac.rbac1ddd.domain.permission.Permission;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import lombok.Getter;

/**
 * 聚合根：用户提交的一条权限申请。
 *
 * <p>申请自己负责状态流转（GRASP 信息专家）：待审核 → 已通过 / 已拒绝 / 已满足 / 已撤回，终态不再改变。
 * 能否提交由 {@link RequestCatalog} 判断，审批时能否选择某个角色由 {@link ApprovalCandidates} 判断。
 * 字段不设 final（CloneFactory 不拷贝 final 字段），但只提供 getter，状态只能通过业务方法改变。
 */
@Getter
public class PermissionRequest {
  /** 撤回或查看他人申请时的统一提示：不暴露申请是否存在。 */
  static final String NOT_FOUND_DETAIL = "申请不存在。";

  private Long id;
  private long applicantUserId;
  private long permissionId;
  private String reason;
  private RequestStatus status;
  private Long reviewerUserId;
  private Long approvedRoleId;
  private LocalDateTime createdAt;
  private LocalDateTime reviewedAt;

  /**
   * 由仓储重建申请，或由 {@link #submit} 创建新申请。
   *
   * @param id 申请 ID，新申请为 null，保存后由 {@link #assignId} 回填
   * @param applicantUserId 申请人用户 ID
   * @param permissionId 所申请的权限 ID
   * @param reason 申请理由
   * @param status 当前状态
   * @param reviewerUserId 审核人用户 ID，未审核时为 null
   * @param approvedRoleId 审批通过时分配的业务角色 ID，其他状态为 null
   * @param createdAt 提交时间（UTC）
   * @param reviewedAt 审核时间（UTC），未审核时为 null
   */
  public PermissionRequest(
      Long id,
      long applicantUserId,
      long permissionId,
      String reason,
      RequestStatus status,
      Long reviewerUserId,
      Long approvedRoleId,
      LocalDateTime createdAt,
      LocalDateTime reviewedAt) {
    this.id = id;
    this.applicantUserId = applicantUserId;
    this.permissionId = permissionId;
    this.reason = reason;
    this.status = status;
    this.reviewerUserId = reviewerUserId;
    this.approvedRoleId = approvedRoleId;
    this.createdAt = createdAt;
    this.reviewedAt = reviewedAt;
  }

  /**
   * 工厂方法：提交权限申请。申请目录确认可以申请后，才创建待审核的申请。
   *
   * @param applicantUserId 申请人，即当前登录用户
   * @param permissionId 所申请的菜单或按钮权限 ID
   * @param reason 申请理由，去掉首尾空白后保存
   * @param catalog 申请人的申请目录
   * @param alreadyPending 申请人是否已有该权限的待审核申请
   * @return 尚未保存、ID 为 null 的待审核申请
   * @throws DomainException 权限不存在为 NOT_FOUND，属于管理员职责为 FORBIDDEN，已拥有、已有待审核申请或缺少所属菜单为 CONFLICT
   */
  public static PermissionRequest submit(
      long applicantUserId,
      long permissionId,
      String reason,
      RequestCatalog catalog,
      boolean alreadyPending) {
    Permission target = catalog.requireRequestable(permissionId, alreadyPending);
    return new PermissionRequest(
        null,
        applicantUserId,
        target.id(),
        reason.strip(),
        RequestStatus.PENDING,
        null,
        null,
        LocalDateTime.now(ZoneOffset.UTC),
        null);
  }

  /**
   * 新申请保存后由仓储回填数据库生成的 ID，只能回填一次。
   *
   * @param generatedId 数据库生成的 ID
   * @throws IllegalStateException 申请已有 ID 时，说明调用顺序错误
   */
  public void assignId(long generatedId) {
    if (id != null) throw new IllegalStateException("申请 ID 已存在：" + id);
    id = generatedId;
  }

  /**
   * 判断申请是否由指定用户提交。
   *
   * @param userId 用户 ID
   * @return 是申请人本人时为 true
   */
  public boolean isAppliedBy(long userId) {
    return applicantUserId == userId;
  }

  /**
   * 只有待审核的申请可以撤回或审核。
   *
   * @throws DomainException 申请已处理时为 CONFLICT
   */
  public void ensurePending() {
    if (status != RequestStatus.PENDING) throw new DomainException(CONFLICT, "申请已处理。");
  }

  /**
   * 申请人撤回待审核申请，申请记录保留。
   *
   * @param userId 当前操作者
   * @throws DomainException 不是申请人本人为 NOT_FOUND，申请已处理为 CONFLICT
   */
  public void withdrawBy(long userId) {
    if (!isAppliedBy(userId)) throw new DomainException(NOT_FOUND, NOT_FOUND_DETAIL);
    ensurePending();
    status = RequestStatus.WITHDRAWN;
  }

  /**
   * 用申请人最新的有效权限结算申请：只有待审核且所申请权限已获得时，才变为"已满足"。
   *
   * <p>已审批、已拒绝、已撤回等终态不受授权变化影响；结算不记录审核人。
   *
   * @param grantedPermissionIds 申请人当前的有效权限 ID
   * @return 状态是否发生变化
   */
  public boolean settleWith(Set<Long> grantedPermissionIds) {
    if (status != RequestStatus.PENDING || !grantedPermissionIds.contains(permissionId))
      return false;
    status = RequestStatus.SATISFIED;
    return true;
  }

  /**
   * 审批通过：所选角色必须是可选的候选角色，之后由应用层为申请人建立与该角色的关联。
   *
   * @param roleId 所选业务角色 ID
   * @param reviewerUserId 审核人
   * @param candidates 按申请人当前授权重新计算的候选角色
   * @throws DomainException 申请已处理为 CONFLICT，角色不是候选或附带无关权限为 INVALID_REQUEST
   */
  public void approve(long roleId, long reviewerUserId, ApprovalCandidates candidates) {
    ensurePending();
    candidates.requireSelectable(roleId);
    status = RequestStatus.APPROVED;
    approvedRoleId = roleId;
    markReviewed(reviewerUserId);
  }

  /**
   * 审批拒绝，不分配角色。被拒绝的申请不会因后续授权变化而恢复。
   *
   * @param reviewerUserId 审核人
   * @throws DomainException 申请已处理时为 CONFLICT
   */
  public void reject(long reviewerUserId) {
    ensurePending();
    status = RequestStatus.REJECTED;
    markReviewed(reviewerUserId);
  }

  /** 记录审核人和审核时间（UTC）。 */
  private void markReviewed(long reviewerUserId) {
    this.reviewerUserId = reviewerUserId;
    this.reviewedAt = LocalDateTime.now(ZoneOffset.UTC);
  }
}
