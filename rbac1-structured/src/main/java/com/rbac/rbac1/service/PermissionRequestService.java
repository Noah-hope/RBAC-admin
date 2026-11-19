package com.rbac.rbac1.service;

import static com.rbac.rbac1.service.DepartmentService.isTotalAdmin;
import static com.rbac.rbac1.service.DepartmentService.requireAdministrator;
import static com.rbac.rbac1.service.ManagementFailure.Reason.CONFLICT;
import static com.rbac.rbac1.service.ManagementFailure.Reason.FORBIDDEN;
import static com.rbac.rbac1.service.ManagementFailure.Reason.INVALID_REQUEST;
import static com.rbac.rbac1.service.ManagementFailure.Reason.NOT_FOUND;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.rbac.rbac1.dto.PermissionRequestDTO;
import com.rbac.rbac1.dto.PermissionReviewDTO;
import com.rbac.rbac1.entity.BusinessApplication;
import com.rbac.rbac1.entity.OperationLogTarget;
import com.rbac.rbac1.entity.Permission;
import com.rbac.rbac1.entity.PermissionRequest;
import com.rbac.rbac1.entity.Role;
import com.rbac.rbac1.entity.UserAccount;
import com.rbac.rbac1.entity.UserRole;
import com.rbac.rbac1.mapper.ApplicationMapper;
import com.rbac.rbac1.mapper.PermissionMapper;
import com.rbac.rbac1.mapper.PermissionRequestMapper;
import com.rbac.rbac1.mapper.UserMapper;
import com.rbac.rbac1.mapper.UserRoleMapper;
import com.rbac.rbac1.vo.ApprovalOptionVO;
import com.rbac.rbac1.vo.PageVO;
import com.rbac.rbac1.vo.PermissionRequestVO;
import com.rbac.rbac1.vo.PermissionSummaryVO;
import com.rbac.rbac1.vo.RequestApplicationVO;
import com.rbac.rbac1.vo.RequestGroupVO;
import com.rbac.rbac1.vo.RequestablePermissionsVO;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 权限申请：用户申请自己尚未拥有的权限，管理员通过为其分配一个包含该权限的业务角色来批准。
 *
 * <p>只能批准不附带额外权限的角色，体现最小权限原则。部门管理员只能审核管理范围内用户的申请。
 */
@Service
@RequiredArgsConstructor
public class PermissionRequestService {

  private final PermissionRequestMapper requests;

  private final PermissionMapper permissions;

  private final ApplicationMapper apps;

  private final UserMapper users;

  private final UserRoleMapper assignments;

  private final RolePermissionService effective;

  private final DepartmentService departments;

  private final OperationLogService logs;

  private final RequestStatusService statuses;

  /**
   * 列出当前用户可以申请的权限，按应用和菜单分组。
   *
   * <p>尚未拥有的菜单作为整组申请；已拥有的菜单下列出尚未拥有的按钮。
   *
   * @param actor 当前操作人
   * @return 按应用 ID 排序的可申请权限
   */
  @Transactional(readOnly = true)
  public RequestablePermissionsVO requestable(SessionUser actor) {
    Set<Long> owned = owned(actor.id());
    List<Permission> all = permissions.findAll(Sort.by("id"));
    List<RequestApplicationVO> result = new ArrayList<>();
    for (BusinessApplication app : apps.findAll(Sort.by("id"))) {
      List<RequestGroupVO> groups = new ArrayList<>();
      for (Permission menu : all) {
        if (!menu.getApplicationId().equals(app.getId())
            || !Permission.MENU.equals(menu.getType())
            || !eligible(actor, app, menu)) {
          continue;
        }
        List<PermissionSummaryVO> available = new ArrayList<>();
        if (!owned.contains(menu.getId())) {
          available.add(PermissionSummaryVO.of(menu));
        } else {
          for (Permission button : all) {
            if (menu.getId().equals(button.getParentId())
                && !owned.contains(button.getId())
                && eligible(actor, app, button)) {
              available.add(PermissionSummaryVO.of(button));
            }
          }
        }
        if (!available.isEmpty()) {
          groups.add(new RequestGroupVO(PermissionSummaryVO.of(menu), available));
        }
      }
      result.add(new RequestApplicationVO(app.getId(), app.getName(), app.getCode(), groups));
    }
    return new RequestablePermissionsVO(result);
  }

  /**
   * 提交权限申请。
   *
   * <p>已拥有的权限、已有待审核申请的权限不能再申请；按钮只能在已拥有所属菜单后申请。
   *
   * @param actor 当前操作人
   * @param input 申请的权限和理由
   * @return 提交后的申请
   * @throws ManagementFailure 权限不存在为 NOT_FOUND，属于固定管理员职责为 FORBIDDEN，不满足申请条件为 CONFLICT
   */
  @Transactional
  public PermissionRequestVO submit(SessionUser actor, PermissionRequestDTO input) {
    Permission target =
        permissions
            .findById(input.getPermissionId())
            .orElseThrow(() -> new ManagementFailure(NOT_FOUND, "权限不存在。"));
    BusinessApplication app = apps.findById(target.getApplicationId()).orElseThrow();
    if (!eligible(actor, app, target)) {
      throw new ManagementFailure(FORBIDDEN, "该权限属于固定管理员职责，不能通过业务角色申请。");
    }
    Set<Long> owned = owned(actor.id());
    if (owned.contains(target.getId())
        || target.getParentId() != null && !owned.contains(target.getParentId())
        || requests.existsByApplicantUserIdAndPermissionIdAndStatus(
            actor.id(), target.getId(), PermissionRequest.PENDING)) {
      throw new ManagementFailure(CONFLICT, "已拥有、已有待审核申请，或尚未获得所属菜单。");
    }
    PermissionRequest request = CloneFactory.copy(new PermissionRequest(), input);
    request.setApplicantUserId(actor.id());
    request.setReason(input.getReason().strip());
    request.setStatus(PermissionRequest.PENDING);
    request.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
    requests.save(request);
    log(actor, request, "提交权限申请");
    return view(request, null);
  }

  /**
   * 分页查询权限申请。
   *
   * <p>REVIEW 模式供管理员审核：总管理员看全部，部门管理员看管理范围内用户的申请；其他模式只看自己提交的申请。
   *
   * @param actor 当前操作人
   * @param mode 查询模式，REVIEW 表示审核视角
   * @param status 只查该状态，可为 {@code null}
   * @param page 页码，从 1 开始
   * @param size 每页条数
   * @return 按提交时间倒序的一页申请
   * @throws ManagementFailure REVIEW 模式下操作人不是管理员时为 FORBIDDEN
   */
  @Transactional(readOnly = true)
  public PageVO<PermissionRequestVO> list(
      SessionUser actor, String mode, String status, int page, int size) {
    boolean review = "REVIEW".equals(mode);
    if (review) {
      requireAdministrator(actor);
    }
    List<Long> scope =
        review && !isTotalAdmin(actor)
            ? departments.managedDepartments(actor, null).stream().map(d -> d.getId()).toList()
            : List.<Long>of();
    Page<PermissionRequest> found =
        requests.findAll(
            (root, query, cb) -> {
              List<Predicate> predicates = new ArrayList<>();
              if (!review) {
                predicates.add(cb.equal(root.get("applicantUserId"), actor.id()));
              } else if (!isTotalAdmin(actor)) {
                Subquery<Long> applicant = query.subquery(Long.class);
                Root<UserAccount> account = applicant.from(UserAccount.class);
                applicant.select(account.get("id")).where(account.get("departmentId").in(scope));
                predicates.add(root.get("applicantUserId").in(applicant));
              }
              if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
              }
              return cb.and(predicates.toArray(Predicate[]::new));
            },
            PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    Map<Long, UserAccount> applicants =
        users.findAllById(found.stream().map(r -> r.getApplicantUserId()).distinct().toList()).stream()
            .collect(Collectors.toMap(u -> u.getId(), u -> u));
    Map<Long, Permission> requested =
        permissions
            .findAllById(found.stream().map(r -> r.getPermissionId()).distinct().toList())
            .stream()
            .collect(Collectors.toMap(p -> p.getId(), p -> p));
    return new PageVO<>(
        found.stream()
            .map(
                r ->
                    view(
                        r,
                        applicants.get(r.getApplicantUserId()),
                        requested.get(r.getPermissionId()),
                        null))
            .toList(),
        page,
        size,
        found.getTotalElements());
  }

  /**
   * 查询申请详情。有权审核时附带可批准的角色选项。
   *
   * @param actor 当前操作人
   * @param id 申请 ID
   * @return 申请详情
   * @throws ManagementFailure 申请不存在或不可见时为 NOT_FOUND
   */
  @Transactional(readOnly = true)
  public PermissionRequestVO get(SessionUser actor, long id) {
    PermissionRequest request = requireVisible(actor, id, false);
    return view(
        request,
        canReview(actor, users.findById(request.getApplicantUserId()).orElseThrow())
            ? options(request)
            : null);
  }

  /**
   * 申请人撤回自己的待审核申请。
   *
   * @param actor 当前操作人
   * @param id 申请 ID
   * @return 撤回后的申请
   * @throws ManagementFailure 申请不存在或不是本人提交为 NOT_FOUND，已处理为 CONFLICT
   */
  @Transactional
  public PermissionRequestVO withdraw(SessionUser actor, long id) {
    PermissionRequest request = requireVisible(actor, id, false);
    if (request.getApplicantUserId() != actor.id()) {
      throw new ManagementFailure(NOT_FOUND, "申请不存在。");
    }
    requirePending(request);
    request.setStatus(PermissionRequest.WITHDRAWN);
    log(actor, request, "撤回权限申请");
    return view(request, null);
  }

  /**
   * 审核待处理的申请。
   *
   * <p>申请人在审核前已获得该权限时，申请直接变为「已满足」，不再执行审核决定。批准时为申请人分配所选角色，
   * 该角色必须包含申请的权限且不附带额外权限。
   *
   * @param actor 当前操作人，必须能审核该申请人
   * @param id 申请 ID
   * @param input 审核决定和批准时选择的角色
   * @return 审核后的申请
   * @throws ManagementFailure 申请不存在或不可审核为 NOT_FOUND，已处理为 CONFLICT，所选角色不合适为 INVALID_REQUEST
   */
  @Transactional
  public PermissionRequestVO review(SessionUser actor, long id, PermissionReviewDTO input) {
    PermissionRequest request = requireVisible(actor, id, true);
    requirePending(request);
    if (owned(request.getApplicantUserId()).contains(request.getPermissionId())) {
      request.setStatus(PermissionRequest.SATISFIED);
      return view(request, null);
    }
    if ("APPROVE".equals(input.decision())) {
      ApprovalOptionVO candidate =
          options(request).stream()
              .filter(o -> o.role().id() == input.roleId())
              .findFirst()
              .orElseThrow(() -> new ManagementFailure(INVALID_REQUEST, "请选择包含申请权限的业务角色。"));
      if (!candidate.selectable()) {
        throw new ManagementFailure(INVALID_REQUEST, "该角色附带无关权限，请先配置最小权限角色。");
      }
      UserRole assignment = new UserRole();
      assignment.setUserId(request.getApplicantUserId());
      assignment.setRoleId(input.roleId());
      assignments.save(assignment);
      request.setStatus(PermissionRequest.APPROVED);
      request.setApprovedRoleId(input.roleId());
    } else {
      request.setStatus(PermissionRequest.REJECTED);
    }
    request.setReviewerUserId(actor.id());
    request.setReviewedAt(LocalDateTime.now(ZoneOffset.UTC));
    log(actor, request, "审核权限申请");
    statuses.settleUsers(List.of(request.getApplicantUserId()));
    return view(request, null);
  }

  /**
   * 列出包含申请权限的业务角色作为审核选项，标出各自会额外带来的权限；没有额外权限的才可选。
   */
  private List<ApprovalOptionVO> options(PermissionRequest request) {
    Permission permission = permissions.findById(request.getPermissionId()).orElseThrow();
    RolePermissionService.UserPermissions user = effective.forUser(request.getApplicantUserId());
    RolePermissionService.Graph graph = user.graph();
    Map<Long, Long> counts =
        assignments.countRoleUsers().stream()
            .collect(
                Collectors.toMap(
                    UserRoleMapper.RoleUserCount::getRoleId,
                    UserRoleMapper.RoleUserCount::getUserCount));
    List<ApprovalOptionVO> candidates = new ArrayList<>();
    for (Role role :
        graph.roles().values().stream().sorted(Comparator.comparing(r -> r.getId())).toList()) {
      if (!Role.BUSINESS.equals(role.getRoleType())) {
        continue;
      }
      Set<Long> rights = effective.effectiveIds(graph, role.getId());
      if (!rights.contains(request.getPermissionId())) {
        continue;
      }
      List<Long> extra =
          ApprovalRules.extraPermissions(
              rights, user.sources().keySet(), permission.getId(), permission.getParentId());
      candidates.add(
          new ApprovalOptionVO(
              effective.details(graph, role, counts.getOrDefault(role.getId(), 0L)),
              extra.isEmpty(),
              extra));
    }
    return candidates;
  }

  /**
   * 查找操作人可见的申请：申请人本人可见（审核除外），否则必须有权审核该申请人；不可见时按不存在处理。
   */
  private PermissionRequest requireVisible(SessionUser actor, long id, boolean review) {
    PermissionRequest request =
        requests.findById(id).orElseThrow(() -> new ManagementFailure(NOT_FOUND, "申请不存在。"));
    if (!review && request.getApplicantUserId() == actor.id()) {
      return request;
    }
    if (!canReview(actor, users.findById(request.getApplicantUserId()).orElseThrow())) {
      throw new ManagementFailure(NOT_FOUND, "申请不存在。");
    }
    return request;
  }

  /** 总管理员能审核所有人；部门管理员只能审核管理范围内部门的用户。 */
  private boolean canReview(SessionUser actor, UserAccount user) {
    if (isTotalAdmin(actor)) {
      return true;
    }
    if (!Role.DEPARTMENT_ADMIN.equals(actor.managementRole()) || user.getDepartmentId() == null) {
      return false;
    }
    return departments.managedDepartments(actor, null).stream()
        .anyMatch(d -> d.getId().equals(user.getDepartmentId()));
  }

  /**
   * 判断权限能否通过申请获得。
   *
   * <p>业务应用的权限都可以申请；权限中心只开放用户、授权和审计三类资源，并且只对管理员开放，
   * 其余属于固定管理员职责。
   */
  private boolean eligible(SessionUser actor, BusinessApplication app, Permission permission) {
    if (!BusinessApplication.PERMISSION_CENTER.equals(app.getCode())) {
      return true;
    }
    String resource = permission.getCode().split(":", 2)[0];
    return Set.of("user", "assignment", "audit").contains(resource)
        && (isTotalAdmin(actor) || Role.DEPARTMENT_ADMIN.equals(actor.managementRole()));
  }

  /** 用户当前拥有的有效权限 ID。 */
  private Set<Long> owned(long user) {
    return effective.forUser(user).sources().keySet();
  }

  /** 要求申请仍待审核，否则抛出 CONFLICT。 */
  private void requirePending(PermissionRequest request) {
    if (!PermissionRequest.PENDING.equals(request.getStatus())) {
      throw new ManagementFailure(CONFLICT, "申请已处理。");
    }
  }

  /** 查出申请人和权限后组装申请响应。 */
  private PermissionRequestVO view(PermissionRequest request, List<ApprovalOptionVO> options) {
    return view(
        request,
        users.findById(request.getApplicantUserId()).orElseThrow(),
        permissions.findById(request.getPermissionId()).orElseThrow(),
        options);
  }

  /** 组装申请响应；审核选项只在有权审核时给出，否则为 {@code null}。 */
  private PermissionRequestVO view(
      PermissionRequest request,
      UserAccount user,
      Permission permission,
      List<ApprovalOptionVO> options) {
    return new PermissionRequestVO(
        request.getId(),
        request.getApplicantUserId(),
        user.getName(),
        user.getDepartmentId(),
        PermissionSummaryVO.of(permission),
        request.getReason(),
        request.getStatus(),
        request.getReviewerUserId(),
        request.getApprovedRoleId(),
        request.getCreatedAt().toInstant(ZoneOffset.UTC),
        request.getReviewedAt() == null ? null : request.getReviewedAt().toInstant(ZoneOffset.UTC),
        options);
  }

  /** 写入申请相关的全局操作记录。 */
  private void log(SessionUser actor, PermissionRequest request, String action) {
    logs.recordGlobalChange(
        actor,
        action,
        "权限ID " + request.getPermissionId() + "，状态 " + request.getStatus(),
        OperationLogTarget.PERMISSION_REQUEST,
        request.getId(),
        "权限申请#" + request.getId());
  }
}
