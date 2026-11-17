package com.rbac.rbac1ddd.domain.request;

import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.CONFLICT;

import com.rbac.rbac1ddd.domain.permission.PermissionCatalog;
import com.rbac.rbac1ddd.domain.role.EffectivePermission;
import com.rbac.rbac1ddd.domain.role.Role;
import com.rbac.rbac1ddd.domain.role.RoleConfiguration;
import com.rbac.rbac1ddd.domain.role.RoleHierarchy;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import lombok.Getter;

/**
 * 聚合根：部门管理员提交的角色创建申请。
 *
 * <p>申请只保存角色名称、说明、直接权限和所选父角色的快照，不保存继承权限副本；有效权限在展示时按父角色当前配置计算。
 * 状态只有待审核、已通过、已拒绝三种（取 {@link RequestStatus} 的子集），只能通过业务方法改变。
 * 提交和审批创建角色都使用与正常新建角色相同的规则（{@link Role#createBusiness}）。
 */
@Getter
public class RoleCreationRequest {
  private Long id;
  private long applicantUserId;
  private String roleName;
  private String roleDescription;
  private Set<Long> permissionIds;
  private List<ParentRoleSnapshot> parentRoles;
  private RequestStatus status;
  private Long reviewerUserId;
  private Long createdRoleId;
  private LocalDateTime createdAt;
  private LocalDateTime reviewedAt;

  /**
   * 由仓储重建申请，或由 {@link #submit} 创建新申请。集合会被复制并按 ID 排序。
   *
   * @param id 申请 ID，新申请为 null，保存后由 {@link #assignId} 回填
   * @param applicantUserId 申请人用户 ID
   * @param roleName 申请的角色名称
   * @param roleDescription 申请的角色说明
   * @param permissionIds 申请的直接权限 ID
   * @param parentRoles 所选父角色快照
   * @param status 当前状态
   * @param reviewerUserId 审核人用户 ID，未审核时为 null
   * @param createdRoleId 审批通过后实际创建的角色 ID，其他状态为 null
   * @param createdAt 提交时间（UTC）
   * @param reviewedAt 审核时间（UTC），未审核时为 null
   */
  public RoleCreationRequest(
      Long id,
      long applicantUserId,
      String roleName,
      String roleDescription,
      Set<Long> permissionIds,
      List<ParentRoleSnapshot> parentRoles,
      RequestStatus status,
      Long reviewerUserId,
      Long createdRoleId,
      LocalDateTime createdAt,
      LocalDateTime reviewedAt) {
    this.id = id;
    this.applicantUserId = applicantUserId;
    this.roleName = roleName;
    this.roleDescription = roleDescription;
    this.permissionIds = new TreeSet<>(permissionIds);
    this.parentRoles =
        parentRoles.stream().sorted(Comparator.comparingLong(ParentRoleSnapshot::roleId)).toList();
    this.status = status;
    this.reviewerUserId = reviewerUserId;
    this.createdRoleId = createdRoleId;
    this.createdAt = createdAt;
    this.reviewedAt = reviewedAt;
  }

  /**
   * 工厂方法：提交角色创建申请。按新建角色的规则校验配置，校验通过才保存申请；提交不会创建角色。
   *
   * @param applicantUserId 申请人（部门管理员）
   * @param config 申请的角色配置
   * @param hierarchy 当前角色继承图
   * @param catalog 权限目录
   * @return 尚未保存、ID 为 null 的待审核申请
   * @throws DomainException 配置不符合新建角色规则时为 INVALID_REQUEST
   */
  public static RoleCreationRequest submit(
      long applicantUserId,
      RoleConfiguration config,
      RoleHierarchy hierarchy,
      PermissionCatalog catalog) {
    Role.createBusiness(config, hierarchy, catalog);
    return new RoleCreationRequest(
        null,
        applicantUserId,
        config.name(),
        config.description(),
        config.permissionIds(),
        config.parentRoleIds().stream()
            .map(roleId -> new ParentRoleSnapshot(roleId, hierarchy.nameOf(roleId)))
            .toList(),
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
   * 返回直接权限的只读视图。
   *
   * @return 按 ID 升序、不可修改的直接权限 ID
   */
  public Set<Long> getPermissionIds() {
    return Collections.unmodifiableSet(permissionIds);
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
   * 只有待审核的申请可以审核。
   *
   * @throws DomainException 申请已处理时为 CONFLICT
   */
  public void ensurePending() {
    if (status != RequestStatus.PENDING) throw alreadyHandled();
  }

  /**
   * 角色已按最终配置创建成功后，申请转为已通过并记录实际创建的角色。
   *
   * @param roleId 实际创建的角色 ID
   * @param reviewerUserId 审核人
   * @throws DomainException 申请已处理时为 CONFLICT
   */
  public void approve(long roleId, long reviewerUserId) {
    ensurePending();
    status = RequestStatus.APPROVED;
    createdRoleId = roleId;
    markReviewed(reviewerUserId);
  }

  /**
   * 拒绝申请，不创建角色。
   *
   * @param reviewerUserId 审核人
   * @throws DomainException 申请已处理时为 CONFLICT
   */
  public void reject(long reviewerUserId) {
    ensurePending();
    status = RequestStatus.REJECTED;
    markReviewed(reviewerUserId);
  }

  /**
   * 按父角色当前配置预览有效权限。任一父角色已被删除时无法预览。
   *
   * @param hierarchy 当前角色继承图
   * @return 按权限 ID 排序的有效权限；有父角色已删除时为空
   */
  public Optional<List<EffectivePermission>> previewIn(RoleHierarchy hierarchy) {
    List<Long> parentIds = parentRoles.stream().map(ParentRoleSnapshot::roleId).toList();
    if (!parentIds.stream().allMatch(hierarchy::contains)) return Optional.empty();
    return Optional.of(hierarchy.preview(permissionIds, parentIds));
  }

  /** 记录审核人和审核时间（UTC）。 */
  private void markReviewed(long reviewerUserId) {
    this.reviewerUserId = reviewerUserId;
    this.reviewedAt = LocalDateTime.now(ZoneOffset.UTC);
  }

  /** 构造申请已处理的领域异常。 */
  private static DomainException alreadyHandled() {
    return new DomainException(CONFLICT, "申请已处理。");
  }
}
