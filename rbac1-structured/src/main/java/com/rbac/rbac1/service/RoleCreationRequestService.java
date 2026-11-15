package com.rbac.rbac1.service;

import static com.rbac.rbac1.service.DepartmentService.*;
import static com.rbac.rbac1.service.ManagementFailure.Reason.*;

import com.rbac.rbac1.dto.RequestDtos.*;
import com.rbac.rbac1.dto.RoleDtos.RoleInput;
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
public class RoleCreationRequestService {
  private final RoleCreationRequestMapper requests;
  private final RoleCreationRequestPermissionMapper links;
  private final RoleService roles;
  private final RoleCreationRequestInheritanceMapper parents;
  private final RolePermissionService effective;
  private final UserMapper users;
  private final OperationLogService logs;

  public RoleCreationRequestService(
      RoleCreationRequestMapper requests,
      RoleCreationRequestPermissionMapper links,
      RoleService roles,
      RoleCreationRequestInheritanceMapper parents,
      RolePermissionService effective,
      UserMapper users,
      OperationLogService logs) {
    this.requests = requests;
    this.links = links;
    this.roles = roles;
    this.parents = parents;
    this.effective = effective;
    this.users = users;
    this.logs = logs;
  }

  @Transactional
  public RoleCreationView submit(SessionUser actor, RoleInput input) {
    if (!"DEPARTMENT_ADMIN".equals(actor.managementRole()))
      throw new ManagementFailure(FORBIDDEN, "仅部门管理员提交角色创建申请。");
    var selectedParents = roles.validateNew(input);
    var request = new RoleCreationRequest();
    request.applicantUserId = actor.id();
    request.roleName = input.name;
    request.roleDescription = input.description;
    request.status = "PENDING";
    request.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    requests.save(request);
    links.saveAll(
        input.permissionIds.stream()
            .map(
                id -> {
                  var link = new RoleCreationRequestPermission();
                  link.requestId = request.id;
                  link.permissionId = id;
                  return link;
                })
            .toList());
    parents.saveAll(
        selectedParents.stream()
            .map(
                parent -> {
                  var link = new RoleCreationRequestInheritance();
                  link.requestId = request.id;
                  link.inheritedRoleId = parent.id();
                  link.inheritedRoleName = parent.name();
                  return link;
                })
            .toList());
    log(actor, request, "提交角色创建申请");
    return view(request);
  }

  @Transactional(readOnly = true)
  public PageResult<RoleCreationView> list(SessionUser actor, String status, int page, int size) {
    requireAdministrator(actor);
    var found =
        requests.findAll(
            (root, query, cb) -> {
              var conditions = new ArrayList<Predicate>();
              if (!isTotalAdmin(actor))
                conditions.add(cb.equal(root.get("applicantUserId"), actor.id()));
              if (status != null) conditions.add(cb.equal(root.get("status"), status));
              return cb.and(conditions.toArray(Predicate[]::new));
            },
            PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    return new PageResult<>(
        views(found.getContent(), effective.load()), page, size, found.getTotalElements());
  }

  @Transactional(readOnly = true)
  public RoleCreationDetails get(SessionUser actor, long id) {
    var request = requireRequest(id);
    if (!isTotalAdmin(actor) && request.applicantUserId != actor.id())
      throw new ManagementFailure(NOT_FOUND, "申请不存在。");
    var graph = effective.load();
    var original = views(List.of(request), graph).getFirst();
    var preview =
        original.inheritedRoles().stream().anyMatch(parent -> !parent.exists())
            ? null
            : effective.preview(
                graph,
                Set.copyOf(original.permissionIds()),
                original.inheritedRoles().stream().map(RequestInheritedRole::id).toList());
    return new RoleCreationDetails(original, preview);
  }

  @Transactional
  public RoleCreationView review(SessionUser actor, long id, RoleCreationReview input) {
    requireTotalAdmin(actor);
    var request = requireRequest(id);
    if (!"PENDING".equals(request.status)) throw new ManagementFailure(CONFLICT, "申请已处理。");
    if ("APPROVE".equals(input.decision())) {
      var role = roles.create(actor, input.role());
      request.createdRoleId = role.id();
      request.status = "APPROVED";
    } else request.status = "REJECTED";
    request.reviewerUserId = actor.id();
    request.reviewedAt = LocalDateTime.now(ZoneOffset.UTC);
    log(actor, request, "审核角色创建申请");
    return view(request);
  }

  private RoleCreationRequest requireRequest(long id) {
    return requests.findById(id).orElseThrow(() -> new ManagementFailure(NOT_FOUND, "申请不存在。"));
  }

  private RoleCreationView view(RoleCreationRequest r) {
    return views(List.of(r), effective.load()).getFirst();
  }

  private List<RoleCreationView> views(
      List<RoleCreationRequest> rows, RolePermissionService.Graph graph) {
    if (rows.isEmpty()) return List.of();
    var ids = rows.stream().map(r -> r.id).toList();
    var names =
        users.findAllById(rows.stream().map(r -> r.applicantUserId).distinct().toList()).stream()
            .collect(Collectors.toMap(u -> u.id, u -> u.name));
    var direct =
        links.findByRequestIdInOrderByPermissionId(ids).stream()
            .collect(
                Collectors.groupingBy(
                    link -> link.requestId,
                    Collectors.mapping(link -> link.permissionId, Collectors.toList())));
    var snapshots =
        parents.findByRequestIdInOrderByInheritedRoleId(ids).stream()
            .collect(
                Collectors.groupingBy(
                    link -> link.requestId,
                    Collectors.mapping(
                        link ->
                            new RequestInheritedRole(
                                link.inheritedRoleId,
                                link.inheritedRoleName,
                                graph.roles().containsKey(link.inheritedRoleId)),
                        Collectors.toList())));
    return rows.stream()
        .map(
            r ->
                new RoleCreationView(
                    r.id,
                    r.applicantUserId,
                    names.get(r.applicantUserId),
                    r.roleName,
                    r.roleDescription,
                    direct.getOrDefault(r.id, List.of()),
                    snapshots.getOrDefault(r.id, List.of()),
                    r.status,
                    r.reviewerUserId,
                    r.createdRoleId,
                    r.createdAt.toInstant(ZoneOffset.UTC),
                    r.reviewedAt == null ? null : r.reviewedAt.toInstant(ZoneOffset.UTC)))
        .toList();
  }

  private void log(SessionUser actor, RoleCreationRequest r, String action) {
    logs.recordGlobalChange(
        actor,
        action,
        "状态 " + r.status + (r.createdRoleId == null ? "" : "，实际创建角色 ID " + r.createdRoleId),
        "ROLE_REQUEST",
        r.id,
        r.roleName);
  }
}
