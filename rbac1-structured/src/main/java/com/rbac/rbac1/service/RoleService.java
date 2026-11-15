package com.rbac.rbac1.service;

import static com.rbac.rbac1.service.DepartmentService.requireAdministrator;
import static com.rbac.rbac1.service.DepartmentService.requireTotalAdmin;
import static com.rbac.rbac1.service.ManagementFailure.Reason.CONFLICT;
import static com.rbac.rbac1.service.ManagementFailure.Reason.INVALID_REQUEST;
import static com.rbac.rbac1.service.ManagementFailure.Reason.NOT_FOUND;

import com.rbac.rbac1.dto.ApiDtos;
import com.rbac.rbac1.dto.RoleDtos.RoleApplicationSummary;
import com.rbac.rbac1.dto.RoleDtos.RoleDetails;
import com.rbac.rbac1.dto.RoleDtos.RoleInput;
import com.rbac.rbac1.dto.RoleDtos.RolePage;
import com.rbac.rbac1.dto.RoleDtos.RoleSummary;
import com.rbac.rbac1.dto.SessionDtos.SessionUser;
import com.rbac.rbac1.entity.Role;
import com.rbac.rbac1.entity.RolePermission;
import com.rbac.rbac1.mapper.PermissionMapper;
import com.rbac.rbac1.mapper.RoleMapper;
import com.rbac.rbac1.mapper.RolePermissionMapper;
import com.rbac.rbac1.mapper.RolePermissionMapper.RoleApplicationRow;
import com.rbac.rbac1.mapper.UserRoleMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {

  private final RolePermissionService effective;
  private final RequestStatusService statuses;
  private final RoleMapper roles;
  private final PermissionMapper permissions;
  private final RolePermissionMapper rolePermissions;
  private final OperationLogService operationLogs;
  private final UserRoleMapper assignments;

  public RoleService(
      RoleMapper roles,
      PermissionMapper permissions,
      RolePermissionMapper rolePermissions,
      OperationLogService operationLogs,
      UserRoleMapper assignments,
      RequestStatusService statuses, RolePermissionService effective) {
    this.effective = effective;
    this.statuses = statuses;
    this.roles = roles;
    this.permissions = permissions;
    this.rolePermissions = rolePermissions;
    this.operationLogs = operationLogs;
    this.assignments = assignments;
  }

  @Transactional(readOnly = true)
  public RolePage list(SessionUser actor, int page, int pageSize, String keyword) {
    requireAdministrator(actor);
    var matches = roles.searchBusinessRoles(keyword, PageRequest.of(page - 1, pageSize));
    var graph=effective.load();
    var items=matches.stream().map(role -> effective.summary(graph,role)).toList();
    return new RolePage(items, page, pageSize, matches.getTotalElements());
  }

  @Transactional(readOnly = true)
  public RoleDetails get(SessionUser actor, long roleId) {
    requireAdministrator(actor);
    var role =
        roles
            .findByIdAndRoleType(roleId, "BUSINESS")
            .orElseThrow(() -> new ManagementFailure(NOT_FOUND, "业务角色不存在。"));
    return details(role);
  }

  @Transactional
  public RoleDetails create(SessionUser actor, RoleInput request) {
    requireTotalAdmin(actor);
    validatePermissionSelection(Set.copyOf(request.permissionIds));
    var role = new Role();
    role.name = request.name;
    role.description = request.description;
    role.roleType = "BUSINESS";
    roles.save(role);
    addPermissions(role.id, request.permissionIds);
    operationLogs.recordGlobalChange(
        actor, "新增角色", "配置权限 ID " + request.permissionIds, "ROLE", role.id, role.name);
    return details(role);
  }

  @Transactional
  public RoleDetails update(SessionUser actor, long roleId, RoleInput request) {
    requireTotalAdmin(actor);
    var role =
        roles
            .findBusinessRoleForUpdate(roleId)
            .orElseThrow(() -> new ManagementFailure(NOT_FOUND, "业务角色不存在。"));
    var requested = Set.copyOf(request.permissionIds);
    validatePermissionSelection(requested);

    var existing = rolePermissions.findByRoleIdOrderByPermissionIdAsc(roleId);
    var existingIds = existing.stream().map(link -> link.permissionId).collect(Collectors.toSet());
    var removed = existing.stream().filter(link -> !requested.contains(link.permissionId)).toList();
    var added = request.permissionIds.stream().filter(id -> !existingIds.contains(id)).toList();
    rolePermissions.deleteAll(removed);
    addPermissions(roleId, added);

    var changes = new ArrayList<String>();
    if (!role.name.equals(request.name)) {
      changes.add("名称从「" + role.name + "」改为「" + request.name + "」");
      role.name = request.name;
    }
    if (!role.description.equals(request.description)) {
      changes.add("说明从「" + role.description + "」改为「" + request.description + "」");
      role.description = request.description;
    }
    if (!added.isEmpty()) {
      changes.add("增加权限 ID " + added);
    }
    if (!removed.isEmpty()) {
      changes.add("移除权限 ID " + removed.stream().map(link -> link.permissionId).toList());
    }
    if (!changes.isEmpty()) {
      operationLogs.recordGlobalChange(
          actor, "修改角色", String.join("，", changes), "ROLE", role.id, role.name);
    }
    statuses.settle();
    return details(role);
  }

  @Transactional
  public void delete(SessionUser actor, long roleId) {
    requireTotalAdmin(actor);
    var role =
        roles
            .findBusinessRoleForUpdate(roleId)
            .orElseThrow(() -> new ManagementFailure(NOT_FOUND, "业务角色不存在。"));
    long assignedUsers = assignments.countByRoleId(roleId);
    if (assignedUsers > 0) {
      throw new ManagementFailure(CONFLICT, "该角色仍关联 " + assignedUsers + " 个用户，请先撤销用户关联。");
    }

    rolePermissions.deleteAll(rolePermissions.findByRoleIdOrderByPermissionIdAsc(roleId));
    rolePermissions.flush();
    roles.delete(role);
    operationLogs.recordGlobalChange(
        actor, "删除角色", "删除未关联用户的业务角色及其权限关联", "ROLE", role.id, role.name);
  }

  void validatePermissionSelection(Set<Long> ids) {
    var selected = permissions.findAllById(ids);
    if (selected.size() != ids.size()) {
      throw new ManagementFailure(INVALID_REQUEST, "选择的权限不存在。");
    }
    if (selected.stream()
        .anyMatch(
            permission -> permission.parentId != null && !ids.contains(permission.parentId))) {
      throw new ManagementFailure(INVALID_REQUEST, "勾选按钮时必须同时勾选所属菜单。");
    }
  }

  private void addPermissions(long roleId, List<Long> permissionIds) {
    var links =
        permissionIds.stream()
            .map(
                id -> {
                  var link = new RolePermission();
                  link.roleId = roleId;
                  link.permissionId = id;
                  return link;
                })
            .toList();
    rolePermissions.saveAll(links);
  }

  private RoleDetails details(Role role) {
    return effective.details(effective.load(),role,assignments.countByRoleId(role.id));
  }
}
