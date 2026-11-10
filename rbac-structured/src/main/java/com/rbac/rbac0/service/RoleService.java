package com.rbac.rbac0.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.rbac.rbac0.dto.RoleDtos.RoleApplicationSummary;
import com.rbac.rbac0.dto.RoleDtos.RoleDetails;
import com.rbac.rbac0.dto.RoleDtos.RoleInput;
import com.rbac.rbac0.dto.RoleDtos.RolePage;
import com.rbac.rbac0.dto.RoleDtos.RoleSummary;
import com.rbac.rbac0.dto.SessionDtos.SessionUser;
import com.rbac.rbac0.entity.Role;
import com.rbac.rbac0.entity.RolePermission;
import com.rbac.rbac0.mapper.PermissionMapper;
import com.rbac.rbac0.mapper.RoleMapper;
import com.rbac.rbac0.mapper.RolePermissionMapper;
import com.rbac.rbac0.mapper.RolePermissionMapper.RoleApplicationRow;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.rbac.rbac0.service.DepartmentService.requireAdministrator;
import static com.rbac.rbac0.service.DepartmentService.requireTotalAdmin;
import static com.rbac.rbac0.service.ManagementFailure.Reason.INVALID_REQUEST;
import static com.rbac.rbac0.service.ManagementFailure.Reason.NOT_FOUND;

@Service
public class RoleService {

    private final RoleMapper roles;
    private final PermissionMapper permissions;
    private final RolePermissionMapper rolePermissions;
    private final OperationLogService operationLogs;

    public RoleService(RoleMapper roles, PermissionMapper permissions, RolePermissionMapper rolePermissions,
                       OperationLogService operationLogs) {
        this.roles = roles;
        this.permissions = permissions;
        this.rolePermissions = rolePermissions;
        this.operationLogs = operationLogs;
    }

    @Transactional(readOnly = true)
    public RolePage list(SessionUser actor, int page, int pageSize, String keyword) {
        requireAdministrator(actor);
        var matches = roles.searchBusinessRoles(keyword, PageRequest.of(page - 1, pageSize));
        var ids = matches.stream().map(role -> role.id).toList();
        var summaries = ids.isEmpty() ? Map.<Long, List<RoleApplicationSummary>>of()
                : rolePermissions.summarizeApplications(ids).stream().collect(Collectors.groupingBy(
                        RoleApplicationRow::getRoleId, Collectors.mapping(RoleService::applicationSummary, Collectors.toList())));
        var items = matches.stream().map(role -> new RoleSummary(role.id, role.name, role.description,
                summaries.getOrDefault(role.id, List.of()))).toList();
        return new RolePage(items, page, pageSize, matches.getTotalElements());
    }

    @Transactional(readOnly = true)
    public RoleDetails get(SessionUser actor, long roleId) {
        requireTotalAdmin(actor);
        var role = roles.findByIdAndRoleType(roleId, "BUSINESS")
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
        operationLogs.recordGlobalChange(actor, "新增角色", "配置权限 ID " + request.permissionIds,
                "ROLE", role.id, role.name);
        return details(role);
    }

    @Transactional
    public RoleDetails update(SessionUser actor, long roleId, RoleInput request) {
        requireTotalAdmin(actor);
        var role = roles.findBusinessRoleForUpdate(roleId)
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
            operationLogs.recordGlobalChange(actor, "修改角色", String.join("，", changes), "ROLE", role.id, role.name);
        }
        return details(role);
    }

    private void validatePermissionSelection(Set<Long> ids) {
        var selected = permissions.findAllById(ids);
        if (selected.size() != ids.size()) {
            throw new ManagementFailure(INVALID_REQUEST, "选择的权限不存在。");
        }
        if (selected.stream().anyMatch(permission -> permission.parentId != null && !ids.contains(permission.parentId))) {
            throw new ManagementFailure(INVALID_REQUEST, "勾选按钮时必须同时勾选所属菜单。");
        }
    }

    private void addPermissions(long roleId, List<Long> permissionIds) {
        var links = permissionIds.stream().map(id -> {
            var link = new RolePermission();
            link.roleId = roleId;
            link.permissionId = id;
            return link;
        }).toList();
        rolePermissions.saveAll(links);
    }

    private RoleDetails details(Role role) {
        var permissionIds = rolePermissions.findByRoleIdOrderByPermissionIdAsc(role.id).stream()
                .map(link -> link.permissionId).toList();
        var applications = rolePermissions.summarizeApplications(List.of(role.id)).stream()
                .map(RoleService::applicationSummary).toList();
        return new RoleDetails(role.id, role.name, role.description, applications, permissionIds);
    }

    private static RoleApplicationSummary applicationSummary(RoleApplicationRow row) {
        return new RoleApplicationSummary(row.getApplicationId(), row.getApplicationName(), row.getPermissionCount());
    }
}
