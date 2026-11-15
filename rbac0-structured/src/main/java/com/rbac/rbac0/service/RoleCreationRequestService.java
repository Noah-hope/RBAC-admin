package com.rbac.rbac0.service;

import static com.rbac.rbac0.service.DepartmentService.*;
import static com.rbac.rbac0.service.ManagementFailure.Reason.*;

import com.rbac.rbac0.dto.RequestDtos.*;
import com.rbac.rbac0.dto.RoleDtos.RoleInput;
import com.rbac.rbac0.dto.SessionDtos.SessionUser;
import com.rbac.rbac0.entity.*;
import com.rbac.rbac0.mapper.*;
import jakarta.persistence.criteria.Predicate;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleCreationRequestService {
  private final RoleCreationRequestMapper requests;
  private final RoleCreationRequestPermissionMapper links;
  private final RoleService roles;
  private final UserMapper users;
  private final OperationLogService logs;

  public RoleCreationRequestService(
      RoleCreationRequestMapper requests,
      RoleCreationRequestPermissionMapper links,
      RoleService roles,
      UserMapper users,
      OperationLogService logs) {
    this.requests = requests;
    this.links = links;
    this.roles = roles;
    this.users = users;
    this.logs = logs;
  }

  @Transactional
  public RoleCreationView submit(SessionUser actor, RoleInput input) {
    if (!"DEPARTMENT_ADMIN".equals(actor.managementRole()))
      throw new ManagementFailure(FORBIDDEN, "仅部门管理员提交角色创建申请。");
    roles.validatePermissionSelection(Set.copyOf(input.permissionIds));
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
        found.stream().map(this::view).toList(), page, size, found.getTotalElements());
  }

  @Transactional(readOnly = true)
  public RoleCreationView get(SessionUser actor, long id) {
    var request = requireRequest(id);
    if (!isTotalAdmin(actor) && request.applicantUserId != actor.id())
      throw new ManagementFailure(NOT_FOUND, "申请不存在。");
    return view(request);
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
    return new RoleCreationView(
        r.id,
        r.applicantUserId,
        users.findById(r.applicantUserId).orElseThrow().name,
        r.roleName,
        r.roleDescription,
        links.findByRequestIdOrderByPermissionId(r.id).stream().map(l -> l.permissionId).toList(),
        r.status,
        r.reviewerUserId,
        r.createdRoleId,
        r.createdAt.toInstant(ZoneOffset.UTC),
        r.reviewedAt == null ? null : r.reviewedAt.toInstant(ZoneOffset.UTC));
  }

  private void log(SessionUser actor, RoleCreationRequest r, String action) {
    logs.recordGlobalChange(actor, action, "状态 " + r.status, "ROLE_REQUEST", r.id, r.roleName);
  }
}
