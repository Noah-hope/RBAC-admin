package com.rbac.rbac1ddd.application.request;

import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.FORBIDDEN;
import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.NOT_FOUND;

import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.application.role.RoleApplicationService;
import com.rbac.rbac1ddd.application.role.RoleCommand;
import com.rbac.rbac1ddd.application.role.RoleDetailsAssembler;
import com.rbac.rbac1ddd.application.shared.OperationRecorder;
import com.rbac.rbac1ddd.application.shared.OperationTarget;
import com.rbac.rbac1ddd.application.shared.PageResult;
import com.rbac.rbac1ddd.domain.permission.PermissionCatalog;
import com.rbac.rbac1ddd.domain.permission.PermissionRepository;
import com.rbac.rbac1ddd.domain.request.RequestStatus;
import com.rbac.rbac1ddd.domain.request.ReviewDecision;
import com.rbac.rbac1ddd.domain.request.RoleCreationRequest;
import com.rbac.rbac1ddd.domain.request.RoleCreationRequestRepository;
import com.rbac.rbac1ddd.domain.role.RoleHierarchy;
import com.rbac.rbac1ddd.domain.role.RoleRepository;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.shared.DomainPage;
import com.rbac.rbac1ddd.domain.user.ManagementRole;
import com.rbac.rbac1ddd.domain.user.User;
import com.rbac.rbac1ddd.domain.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用服务：角色创建申请的提交、查询和审核。
 *
 * <p>提交时的配置校验和状态流转在 {@link RoleCreationRequest} 中；审批通过时调用正常的新建角色用例
 * {@link RoleApplicationService#create}，按父角色最新配置执行与新建角色相同的规则（{@code Role.createBusiness}）
 * 并写入「新增角色」记录。角色、关联、审批结果和操作记录在同一事务中保存，创建失败时整个事务回滚，申请保持待审核。
 */
@Service
@RequiredArgsConstructor
public class RoleCreationRequestApplicationService {
  private final RoleCreationRequestRepository requests;
  private final RoleRepository roles;
  private final PermissionRepository permissions;
  private final UserRepository users;
  private final RoleApplicationService roleCreation;
  private final OperationRecorder recorder;

  /**
   * 部门管理员提交角色创建申请；提交不会创建角色，也不改变任何用户的权限。
   *
   * @param operator 当前登录用户，必须是部门管理员
   * @param command 申请的角色配置
   * @return 新提交的申请
   */
  @Transactional
  public RoleCreationRequestResult submit(Operator operator, RoleCommand command) {
    if (operator.managementRole() != ManagementRole.DEPARTMENT_ADMIN)
      throw new DomainException(FORBIDDEN, "仅部门管理员提交角色创建申请。");
    RoleHierarchy hierarchy = roles.loadHierarchy();
    RoleCreationRequest request =
        RoleCreationRequest.submit(
            operator.id(), command.toConfiguration(), hierarchy, permissions.loadCatalog());
    requests.save(request);
    record(operator, request, "提交角色创建申请");
    return views(List.of(request), hierarchy).getFirst();
  }

  /**
   * 分页查询申请：部门管理员只看本人提交的，总管理员看全部。
   *
   * @param operator 当前登录用户，必须是管理员
   * @param status 状态筛选，null 表示全部状态
   * @param page 页码，从 1 开始
   * @param pageSize 每页条数
   * @return 当前页申请，按提交时间和 ID 倒序
   */
  @Transactional(readOnly = true)
  public PageResult<RoleCreationRequestResult> list(
      Operator operator, RequestStatus status, int page, int pageSize) {
    operator.requireAdministrator();
    DomainPage<RoleCreationRequest> found =
        requests.search(operator.isTotalAdmin() ? null : operator.id(), status, page, pageSize);
    return new PageResult<>(
        views(found.items(), roles.loadHierarchy()), page, pageSize, found.total());
  }

  /**
   * 查看申请详情：提交者本人或总管理员可见，有效权限按父角色当前配置计算；有父角色已删除时不提供预览。
   *
   * @param operator 当前登录用户
   * @param requestId 申请 ID
   * @return 申请详情
   */
  @Transactional(readOnly = true)
  public RoleCreationDetailsResult get(Operator operator, long requestId) {
    RoleCreationRequest request = require(requestId);
    if (!operator.isTotalAdmin() && !request.isAppliedBy(operator.id())) throw notFound();
    RoleHierarchy hierarchy = roles.loadHierarchy();
    PermissionCatalog catalog = permissions.loadCatalog();
    return new RoleCreationDetailsResult(
        views(List.of(request), hierarchy).getFirst(),
        request
            .previewIn(hierarchy)
            .map(preview -> RoleDetailsAssembler.effectivePermissions(preview, hierarchy, catalog))
            .orElse(null));
  }

  /**
   * 总管理员审核申请：通过时按提交的最终配置实际创建角色，拒绝时不创建角色；都不会给员工分配角色。
   *
   * @param operator 当前登录用户，必须是总管理员
   * @param requestId 申请 ID
   * @param decision 审核结论
   * @param role 通过时的最终角色配置，拒绝时为 null
   * @return 审核后的申请
   */
  @Transactional
  public RoleCreationRequestResult review(
      Operator operator, long requestId, ReviewDecision decision, RoleCommand role) {
    operator.requireTotalAdmin();
    RoleCreationRequest request = require(requestId);
    request.ensurePending();
    if (decision == ReviewDecision.APPROVE) {
      request.approve(roleCreation.create(operator, role).id(), operator.id());
    } else {
      request.reject(operator.id());
    }
    requests.save(request);
    record(operator, request, "审核角色创建申请");
    return views(List.of(request), roles.loadHierarchy()).getFirst();
  }

  /** 读取申请，不存在时为 NOT_FOUND。 */
  private RoleCreationRequest require(long requestId) {
    return requests.findById(requestId).orElseThrow(this::notFound);
  }

  /** 写入全局操作记录，明细与 rbac1-structured 相同。 */
  private void record(Operator operator, RoleCreationRequest request, String action) {
    recorder.recordGlobal(
        operator,
        action,
        "状态 "
            + request.getStatus().name()
            + (request.getCreatedRoleId() == null
                ? ""
                : "，实际创建角色 ID " + request.getCreatedRoleId()),
        OperationTarget.roleCreationRequest(request));
  }

  /** 组装申请结果；父角色快照按当前继承图标出是否仍存在。 */
  private List<RoleCreationRequestResult> views(
      List<RoleCreationRequest> rows, RoleHierarchy hierarchy) {
    if (rows.isEmpty()) return List.of();
    Map<Long, String> names =
        users
            .findAllById(
                rows.stream().map(RoleCreationRequest::getApplicantUserId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(User::getId, User::getName));
    return rows.stream()
        .map(
            r ->
                new RoleCreationRequestResult(
                    r.getId(),
                    r.getApplicantUserId(),
                    names.get(r.getApplicantUserId()),
                    r.getRoleName(),
                    r.getRoleDescription(),
                    List.copyOf(r.getPermissionIds()),
                    r.getParentRoles().stream()
                        .map(
                            p ->
                                new RoleCreationRequestResult.InheritedRole(
                                    p.roleId(), p.name(), hierarchy.contains(p.roleId())))
                        .toList(),
                    r.getStatus().name(),
                    r.getReviewerUserId(),
                    r.getCreatedRoleId(),
                    PermissionRequestApplicationService.utc(r.getCreatedAt()),
                    PermissionRequestApplicationService.utc(r.getReviewedAt())))
        .toList();
  }

  /** 申请不存在或不可见。 */
  private DomainException notFound() {
    return new DomainException(NOT_FOUND, "申请不存在。");
  }
}
