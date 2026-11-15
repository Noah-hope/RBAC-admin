package com.rbac.rbac1.service;

import static com.rbac.rbac1.service.DepartmentService.*;
import static com.rbac.rbac1.service.ManagementFailure.Reason.*;

import com.rbac.rbac1.dto.ApiDtos.PermissionSummary;
import com.rbac.rbac1.dto.RequestDtos.*;
import com.rbac.rbac1.dto.SessionDtos.SessionUser;
import com.rbac.rbac1.entity.*;
import com.rbac.rbac1.mapper.*;
import jakarta.persistence.criteria.Predicate;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
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

  public PermissionRequestService(
      PermissionRequestMapper requests,
      PermissionMapper permissions,
      ApplicationMapper apps,
      UserMapper users,
      UserRoleMapper assignments,
      RolePermissionService effective,
      DepartmentService departments,
      OperationLogService logs,
      RequestStatusService statuses) {
    this.requests = requests;
    this.permissions = permissions;
    this.apps = apps;
    this.users = users;
    this.assignments = assignments;
    this.effective = effective;
    this.departments = departments;
    this.logs = logs;
    this.statuses = statuses;
  }

  @Transactional(readOnly = true)
  public RequestablePermissions requestable(SessionUser actor) {
    var owned = owned(actor.id());
    var all = permissions.findAll(Sort.by("id"));
    var result = new ArrayList<RequestApplication>();
    for (var app : apps.findAll(Sort.by("id"))) {
      var groups = new ArrayList<RequestGroup>();
      for (var menu : all) {
        if (!menu.applicationId.equals(app.id)
            || !"MENU".equals(menu.type)
            || !eligible(actor, app, menu)) continue;
        var available = new ArrayList<PermissionSummary>();
        if (!owned.contains(menu.id)) {
          available.add(PermissionSummary.of(menu));
        } else {
          for (var button : all) {
            if (menu.id.equals(button.parentId)
                && !owned.contains(button.id)
                && eligible(actor, app, button)) available.add(PermissionSummary.of(button));
          }
        }
        if (!available.isEmpty())
          groups.add(new RequestGroup(PermissionSummary.of(menu), available));
      }
      result.add(new RequestApplication(app.id, app.name, app.code, groups));
    }
    return new RequestablePermissions(result);
  }

  @Transactional
  public PermissionRequestView submit(SessionUser actor, PermissionRequestInput input) {
    var target =
        permissions
            .findById(input.permissionId())
            .orElseThrow(() -> new ManagementFailure(NOT_FOUND, "权限不存在。"));
    var app = apps.findById(target.applicationId).orElseThrow();
    if (!eligible(actor, app, target))
      throw new ManagementFailure(FORBIDDEN, "该权限属于固定管理员职责，不能通过业务角色申请。");
    var owned = owned(actor.id());
    if (owned.contains(target.id)
        || target.parentId != null && !owned.contains(target.parentId)
        || requests.existsByApplicantUserIdAndPermissionIdAndStatus(
            actor.id(), target.id, "PENDING"))
      throw new ManagementFailure(CONFLICT, "已拥有、已有待审核申请，或尚未获得所属菜单。");
    var request = new PermissionRequest();
    request.applicantUserId = actor.id();
    request.permissionId = target.id;
    request.reason = input.reason().strip();
    request.status = "PENDING";
    request.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    requests.save(request);
    log(actor, request, "提交权限申请");
    return view(request, null);
  }

  @Transactional(readOnly = true)
  public PageResult<PermissionRequestView> list(
      SessionUser actor, String mode, String status, int page, int size) {
    boolean review = "REVIEW".equals(mode);
    if (review) requireAdministrator(actor);
    var scope =
        review && !isTotalAdmin(actor)
            ? departments.managedDepartments(actor, null).stream().map(d -> d.id).toList()
            : List.<Long>of();
    var found =
        requests.findAll(
            (root, query, cb) -> {
              var predicates = new ArrayList<Predicate>();
              if (!review) predicates.add(cb.equal(root.get("applicantUserId"), actor.id()));
              else if (!isTotalAdmin(actor)) {
                var applicant = query.subquery(Long.class);
                var account = applicant.from(UserAccount.class);
                applicant.select(account.get("id")).where(account.get("departmentId").in(scope));
                predicates.add(root.get("applicantUserId").in(applicant));
              }
              if (status != null) predicates.add(cb.equal(root.get("status"), status));
              return cb.and(predicates.toArray(Predicate[]::new));
            },
            PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    var applicants =
        users.findAllById(found.stream().map(r -> r.applicantUserId).distinct().toList()).stream()
            .collect(Collectors.toMap(u -> u.id, u -> u));
    var requested =
        permissions
            .findAllById(found.stream().map(r -> r.permissionId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(p -> p.id, p -> p));
    return new PageResult<>(
        found.stream()
            .map(
                r ->
                    view(r, applicants.get(r.applicantUserId), requested.get(r.permissionId), null))
            .toList(),
        page,
        size,
        found.getTotalElements());
  }

  @Transactional(readOnly = true)
  public PermissionRequestView get(SessionUser actor, long id) {
    var request = requireVisible(actor, id, false);
    return view(
        request,
        canReview(actor, users.findById(request.applicantUserId).orElseThrow())
            ? options(request)
            : null);
  }

  @Transactional
  public PermissionRequestView withdraw(SessionUser actor, long id) {
    var request = requireVisible(actor, id, false);
    if (request.applicantUserId != actor.id()) throw new ManagementFailure(NOT_FOUND, "申请不存在。");
    requirePending(request);
    request.status = "WITHDRAWN";
    log(actor, request, "撤回权限申请");
    return view(request, null);
  }

  @Transactional
  public PermissionRequestView review(SessionUser actor, long id, PermissionReview input) {
    var request = requireVisible(actor, id, true);
    requirePending(request);
    if (owned(request.applicantUserId).contains(request.permissionId)) {
      request.status = "SATISFIED";
      return view(request, null);
    }
    if ("APPROVE".equals(input.decision())) {
      var candidate =
          options(request).stream()
              .filter(o -> o.role().id() == input.roleId())
              .findFirst()
              .orElseThrow(() -> new ManagementFailure(INVALID_REQUEST, "请选择包含申请权限的业务角色。"));
      if (!candidate.selectable())
        throw new ManagementFailure(INVALID_REQUEST, "该角色附带无关权限，请先配置最小权限角色。");
      var assignment = new UserRole();
      assignment.userId = request.applicantUserId;
      assignment.roleId = input.roleId();
      assignments.save(assignment);
      request.status = "APPROVED";
      request.approvedRoleId = input.roleId();
    } else request.status = "REJECTED";
    request.reviewerUserId = actor.id();
    request.reviewedAt = LocalDateTime.now(ZoneOffset.UTC);
    log(actor, request, "审核权限申请");
    statuses.settleUsers(List.of(request.applicantUserId));
    return view(request, null);
  }

  private List<ApprovalOption> options(PermissionRequest request) {
    var permission = permissions.findById(request.permissionId).orElseThrow();
    var user = effective.forUser(request.applicantUserId);
    var graph = user.graph();
    var counts =
        assignments.countRoleUsers().stream()
            .collect(
                Collectors.toMap(
                    UserRoleMapper.RoleUserCount::getRoleId,
                    UserRoleMapper.RoleUserCount::getUserCount));
    var candidates = new ArrayList<ApprovalOption>();
    for (var role :
        graph.roles().values().stream().sorted(Comparator.comparing(r -> r.id)).toList()) {
      if (!"BUSINESS".equals(role.roleType)) continue;
      var rights = effective.effectiveIds(graph, role.id);
      if (!rights.contains(request.permissionId)) continue;
      var extra =
          ApprovalRules.extraPermissions(
              rights, user.sources().keySet(), permission.id, permission.parentId);
      candidates.add(
          new ApprovalOption(
              effective.details(graph, role, counts.getOrDefault(role.id, 0L)),
              extra.isEmpty(),
              extra));
    }
    return candidates;
  }

  private PermissionRequest requireVisible(SessionUser actor, long id, boolean review) {
    var request =
        requests.findById(id).orElseThrow(() -> new ManagementFailure(NOT_FOUND, "申请不存在。"));
    if (!review && request.applicantUserId == actor.id()) return request;
    if (!canReview(actor, users.findById(request.applicantUserId).orElseThrow()))
      throw new ManagementFailure(NOT_FOUND, "申请不存在。");
    return request;
  }

  private boolean canReview(SessionUser actor, UserAccount user) {
    if (isTotalAdmin(actor)) return true;
    if (!"DEPARTMENT_ADMIN".equals(actor.managementRole()) || user.departmentId == null)
      return false;
    return departments.managedDepartments(actor, null).stream()
        .anyMatch(d -> d.id.equals(user.departmentId));
  }

  private boolean eligible(SessionUser actor, BusinessApplication app, Permission permission) {
    if (!"permission-center".equals(app.code)) return true;
    var resource = permission.code.split(":", 2)[0];
    return Set.of("user", "assignment", "audit").contains(resource)
        && (isTotalAdmin(actor) || "DEPARTMENT_ADMIN".equals(actor.managementRole()));
  }

  private Set<Long> owned(long user) {
    return effective.forUser(user).sources().keySet();
  }

  private void requirePending(PermissionRequest request) {
    if (!"PENDING".equals(request.status)) throw new ManagementFailure(CONFLICT, "申请已处理。");
  }

  private PermissionRequestView view(PermissionRequest request, List<ApprovalOption> options) {
    return view(
        request,
        users.findById(request.applicantUserId).orElseThrow(),
        permissions.findById(request.permissionId).orElseThrow(),
        options);
  }

  private PermissionRequestView view(
      PermissionRequest request,
      UserAccount user,
      Permission permission,
      List<ApprovalOption> options) {
    return new PermissionRequestView(
        request.id,
        request.applicantUserId,
        user.name,
        user.departmentId,
        PermissionSummary.of(permission),
        request.reason,
        request.status,
        request.reviewerUserId,
        request.approvedRoleId,
        request.createdAt.toInstant(ZoneOffset.UTC),
        request.reviewedAt == null ? null : request.reviewedAt.toInstant(ZoneOffset.UTC),
        options);
  }

  private void log(SessionUser actor, PermissionRequest request, String action) {
    logs.recordGlobalChange(
        actor,
        action,
        "权限ID " + request.permissionId + "，状态 " + request.status,
        "PERMISSION_REQ",
        request.id,
        "权限申请#" + request.id);
  }
}
