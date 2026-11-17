package com.rbac.rbac1ddd.application.request;

import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.NOT_FOUND;

import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.application.role.RoleDetailsAssembler;
import com.rbac.rbac1ddd.application.shared.OperationRecorder;
import com.rbac.rbac1ddd.application.shared.OperationTarget;
import com.rbac.rbac1ddd.application.shared.PageResult;
import com.rbac.rbac1ddd.domain.app.BusinessApplicationRepository;
import com.rbac.rbac1ddd.domain.department.DepartmentRepository;
import com.rbac.rbac1ddd.domain.permission.PermissionCatalog;
import com.rbac.rbac1ddd.domain.permission.PermissionRepository;
import com.rbac.rbac1ddd.domain.request.ApprovalCandidates;
import com.rbac.rbac1ddd.domain.request.PermissionRequest;
import com.rbac.rbac1ddd.domain.request.PermissionRequestRepository;
import com.rbac.rbac1ddd.domain.request.RequestCatalog;
import com.rbac.rbac1ddd.domain.request.RequestStatus;
import com.rbac.rbac1ddd.domain.request.ReviewDecision;
import com.rbac.rbac1ddd.domain.role.Role;
import com.rbac.rbac1ddd.domain.role.RoleHierarchy;
import com.rbac.rbac1ddd.domain.role.RoleRepository;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.shared.DomainPage;
import com.rbac.rbac1ddd.domain.user.ManagementRole;
import com.rbac.rbac1ddd.domain.user.User;
import com.rbac.rbac1ddd.domain.user.UserRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用服务：权限申请的目录、提交、查询、撤回和审核。
 *
 * <p>只负责编排和权限守卫：能否申请由 {@link RequestCatalog} 判断，候选角色由 {@link ApprovalCandidates} 计算，
 * 状态流转由 {@link PermissionRequest} 完成。审核范围（总管理员全部、部门管理员本部门及下级部门的员工）由
 * {@link Operator} 计算；越界和不存在一样返回"申请不存在"，不暴露申请是否存在。
 */
@Service
@RequiredArgsConstructor
public class PermissionRequestApplicationService {
  private final PermissionRequestRepository requests;
  private final PermissionRepository permissions;
  private final BusinessApplicationRepository applications;
  private final UserRepository users;
  private final RoleRepository roles;
  private final DepartmentRepository departments;
  private final RoleDetailsAssembler assembler;
  private final OperationRecorder recorder;
  private final PermissionRequestSettlement settlement;

  /**
   * 本人可申请的权限目录：按应用和菜单分组，已拥有（直接或继承）的权限不列出。
   *
   * @param operator 当前登录用户
   * @return 全部应用的申请目录
   */
  @Transactional(readOnly = true)
  public RequestablePermissionsResult requestable(Operator operator) {
    RequestCatalog catalog = catalogOf(operator, permissions.loadCatalog());
    return new RequestablePermissionsResult(
        applications.findAll().stream()
            .map(
                app ->
                    new RequestablePermissionsResult.Application(
                        app.getId(),
                        app.getName(),
                        app.getCode(),
                        catalog.groupsIn(app.getId()).stream()
                            .map(
                                group ->
                                    new RequestablePermissionsResult.Group(
                                        PermissionSummaryResult.of(group.menu()),
                                        group.permissions().stream()
                                            .map(PermissionSummaryResult::of)
                                            .toList()))
                            .toList()))
            .toList());
  }

  /**
   * 提交权限申请，申请人取自登录令牌，写入全局操作记录。
   *
   * @param operator 当前登录用户
   * @param permissionId 所申请的权限 ID
   * @param reason 申请理由
   * @return 新提交的申请
   */
  @Transactional
  public PermissionRequestResult submit(Operator operator, long permissionId, String reason) {
    PermissionCatalog catalog = permissions.loadCatalog();
    PermissionRequest request =
        PermissionRequest.submit(
            operator.id(),
            permissionId,
            reason,
            catalogOf(operator, catalog),
            requests.existsPending(operator.id(), permissionId));
    requests.save(request);
    record(operator, request, "提交权限申请");
    return view(request, applicantOf(request), catalog, null);
  }

  /**
   * 分页查询申请：MINE 只看本人提交的；REVIEW 仅限管理员，按审核范围列出。
   *
   * @param operator 当前登录用户
   * @param review true 表示审核视图
   * @param status 状态筛选，null 表示全部状态
   * @param page 页码，从 1 开始
   * @param pageSize 每页条数
   * @return 当前页申请，按提交时间和 ID 倒序
   */
  @Transactional(readOnly = true)
  public PageResult<PermissionRequestResult> list(
      Operator operator, boolean review, RequestStatus status, int page, int pageSize) {
    DomainPage<PermissionRequest> found;
    if (review) {
      operator.requireAdministrator();
      Set<Long> scope =
          operator.isTotalAdmin()
              ? null
              : operator.manageableDepartments(departments.loadTree(), null);
      found = requests.findForReview(scope, status, page, pageSize);
    } else {
      found = requests.findByApplicant(operator.id(), status, page, pageSize);
    }
    Map<Long, User> applicants =
        users
            .findAllById(
                found.items().stream().map(PermissionRequest::getApplicantUserId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
    PermissionCatalog catalog = permissions.loadCatalog();
    return new PageResult<>(
        found.items().stream()
            .map(r -> view(r, applicants.get(r.getApplicantUserId()), catalog, null))
            .toList(),
        page,
        pageSize,
        found.total());
  }

  /**
   * 查看申请：申请人本人或有权审核者可见；有权审核者同时看到按当前授权计算的候选角色。
   *
   * @param operator 当前登录用户
   * @param requestId 申请 ID
   * @return 申请详情
   */
  @Transactional(readOnly = true)
  public PermissionRequestResult get(Operator operator, long requestId) {
    PermissionRequest request = requireVisible(operator, requestId, false);
    User applicant = applicantOf(request);
    PermissionCatalog catalog = permissions.loadCatalog();
    return view(
        request,
        applicant,
        catalog,
        canReview(operator, applicant) ? options(request, applicant, catalog) : null);
  }

  /**
   * 申请人撤回待审核申请，申请记录保留，写入全局操作记录。
   *
   * @param operator 当前登录用户
   * @param requestId 申请 ID
   * @return 撤回后的申请
   */
  @Transactional
  public PermissionRequestResult withdraw(Operator operator, long requestId) {
    PermissionRequest request = requireVisible(operator, requestId, false);
    request.withdrawBy(operator.id());
    requests.save(request);
    record(operator, request, "撤回权限申请");
    return view(request, applicantOf(request), permissions.loadCatalog(), null);
  }

  /**
   * 审核权限申请。
   *
   * <p>申请人已经拥有所申请权限时直接转为已满足，不记录审核人和操作记录；通过时按当前授权重新计算候选角色，
   * 只为申请人建立与所选角色的关联；之后结算申请人的其他待审核申请。
   *
   * @param operator 当前登录用户
   * @param requestId 申请 ID
   * @param decision 审核结论
   * @param roleId 通过时所选业务角色，拒绝时为 null
   * @return 审核后的申请
   */
  @Transactional
  public PermissionRequestResult review(
      Operator operator, long requestId, ReviewDecision decision, Long roleId) {
    PermissionRequest request = requireVisible(operator, requestId, true);
    request.ensurePending();
    User applicant = applicantOf(request);
    RoleHierarchy hierarchy = roles.loadHierarchy();
    PermissionCatalog catalog = permissions.loadCatalog();
    Set<Long> owned = applicant.effectivePermissionIds(hierarchy);
    if (request.settleWith(owned)) {
      requests.save(request);
      return view(request, applicant, catalog, null);
    }
    if (decision == ReviewDecision.APPROVE) {
      request.approve(
          roleId,
          operator.id(),
          ApprovalCandidates.evaluate(catalog.get(request.getPermissionId()), owned, hierarchy));
      applicant.grantBusinessRoles(Set.of(roleId));
      users.save(applicant);
    } else {
      request.reject(operator.id());
    }
    requests.save(request);
    record(operator, request, "审核权限申请");
    settlement.settle(List.of(applicant.getId()));
    return view(request, applicant, catalog, null);
  }

  /** 构造当前用户的申请目录：有效权限包含直接和继承来源，停用账号没有任何权限。 */
  private RequestCatalog catalogOf(Operator operator, PermissionCatalog catalog) {
    User applicant = users.findById(operator.id()).orElseThrow();
    return new RequestCatalog(
        catalog,
        applications.findAll(),
        applicant.effectivePermissionIds(roles.loadHierarchy()),
        operator.managementRole());
  }

  /** 按申请人和全部角色的当前配置计算候选角色，并组装角色详情。 */
  private List<ApprovalOptionResult> options(
      PermissionRequest request, User applicant, PermissionCatalog catalog) {
    List<Role> all = roles.findAll();
    RoleHierarchy hierarchy = RoleHierarchy.of(all);
    Map<Long, Role> byId = all.stream().collect(Collectors.toMap(Role::getId, Function.identity()));
    return ApprovalCandidates.evaluate(
            catalog.get(request.getPermissionId()),
            applicant.effectivePermissionIds(hierarchy),
            hierarchy)
        .all()
        .stream()
        .map(
            candidate ->
                new ApprovalOptionResult(
                    assembler.details(
                        byId.get(candidate.roleId()),
                        hierarchy,
                        catalog,
                        users.countHolding(candidate.roleId())),
                    candidate.selectable(),
                    candidate.extraPermissionIds()))
        .toList();
  }

  /**
   * 读取操作者可见的申请：查看和撤回时申请人本人可见，审核时只看审核范围；不可见与不存在一样处理。
   */
  private PermissionRequest requireVisible(Operator operator, long requestId, boolean review) {
    PermissionRequest request = requests.findById(requestId).orElseThrow(this::notFound);
    if (!review && request.isAppliedBy(operator.id())) return request;
    if (!canReview(operator, applicantOf(request))) throw notFound();
    return request;
  }

  /** 总管理员可审核全部申请；部门管理员只能审核本部门及下级部门员工的申请。 */
  private boolean canReview(Operator operator, User applicant) {
    if (operator.isTotalAdmin()) return true;
    if (operator.managementRole() != ManagementRole.DEPARTMENT_ADMIN
        || applicant.getDepartmentId() == null) return false;
    return operator
        .manageableDepartments(departments.loadTree(), null)
        .contains(applicant.getDepartmentId());
  }

  /** 读取申请人；申请表有外键，申请人一定存在。 */
  private User applicantOf(PermissionRequest request) {
    return users.findById(request.getApplicantUserId()).orElseThrow();
  }

  /** 写入全局操作记录，明细与 rbac1-structured 相同。 */
  private void record(Operator operator, PermissionRequest request, String action) {
    recorder.recordGlobal(
        operator,
        action,
        "权限ID " + request.getPermissionId() + "，状态 " + request.getStatus().name(),
        OperationTarget.permissionRequest(request));
  }

  /** 组装申请结果。 */
  private static PermissionRequestResult view(
      PermissionRequest request,
      User applicant,
      PermissionCatalog catalog,
      List<ApprovalOptionResult> options) {
    return new PermissionRequestResult(
        request.getId(),
        request.getApplicantUserId(),
        applicant.getName(),
        applicant.getDepartmentId(),
        PermissionSummaryResult.of(catalog.get(request.getPermissionId())),
        request.getReason(),
        request.getStatus().name(),
        request.getReviewerUserId(),
        request.getApprovedRoleId(),
        utc(request.getCreatedAt()),
        utc(request.getReviewedAt()),
        options);
  }

  /** 数据库中的 UTC 时间转换为时间点，null 保持 null。 */
  static Instant utc(LocalDateTime time) {
    return time == null ? null : time.toInstant(ZoneOffset.UTC);
  }

  /** 申请不存在或不可见。 */
  private DomainException notFound() {
    return new DomainException(NOT_FOUND, "申请不存在。");
  }
}
