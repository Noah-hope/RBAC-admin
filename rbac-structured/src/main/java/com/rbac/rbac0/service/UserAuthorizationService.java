package com.rbac.rbac0.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.rbac.rbac0.dto.AuthorizationDtos.BatchRoleChange;
import com.rbac.rbac0.dto.AuthorizationDtos.BatchRoleResult;
import com.rbac.rbac0.dto.AuthorizationDtos.UserRoleResult;
import com.rbac.rbac0.dto.SessionDtos.SessionUser;
import com.rbac.rbac0.entity.Role;
import com.rbac.rbac0.entity.UserAccount;
import com.rbac.rbac0.entity.UserRole;
import com.rbac.rbac0.mapper.RoleMapper;
import com.rbac.rbac0.mapper.UserMapper;
import com.rbac.rbac0.mapper.UserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.rbac.rbac0.service.DepartmentService.isTotalAdmin;
import static com.rbac.rbac0.service.DepartmentService.requireAdministrator;
import static com.rbac.rbac0.service.ManagementFailure.Reason.*;

@Service
public class UserAuthorizationService {

    private final UserMapper users;
    private final DepartmentService departments;
    private final RoleMapper roles;
    private final UserRoleMapper assignments;
    private final OperationLogService operationLogs;

    public UserAuthorizationService(UserMapper users, DepartmentService departments, RoleMapper roles,
                                    UserRoleMapper assignments, OperationLogService operationLogs) {
        this.users = users;
        this.departments = departments;
        this.roles = roles;
        this.assignments = assignments;
        this.operationLogs = operationLogs;
    }

    @Transactional
    public UserRoleResult replace(SessionUser actor, long userId, List<Long> roleIds) {
        var user = lockTargets(actor, List.of(userId)).getFirst();
        requireBusinessRoles(roleIds);
        var requested = Set.copyOf(roleIds);
        var existing = assignments.findBusinessAssignments(List.of(userId));
        var existingIds = existing.stream().map(link -> link.roleId).collect(Collectors.toSet());
        var added = roleIds.stream().filter(id -> !existingIds.contains(id)).toList();
        var removed = existing.stream().filter(link -> !requested.contains(link.roleId)).toList();

        assignments.deleteAll(removed);
        assignments.saveAll(added.stream().map(id -> assignment(userId, id)).toList());
        if (!added.isEmpty() || !removed.isEmpty()) {
            var detail = new ArrayList<String>();
            if (!added.isEmpty()) {
                detail.add("分配角色 ID " + added);
            }
            if (!removed.isEmpty()) {
                detail.add("撤销角色 ID " + removed.stream().map(link -> link.roleId).toList());
            }
            operationLogs.recordUserChange(actor, "修改用户授权", String.join("，", detail), user, user.departmentId);
        }
        return new UserRoleResult(userId, roleIds.stream().sorted().toList());
    }

    @Transactional
    public BatchRoleResult batch(SessionUser actor, BatchRoleChange request) {
        var targets = lockTargets(actor, request.userIds());
        var businessRoles = requireBusinessRoles(request.roleIds());
        var selected = Set.copyOf(request.roleIds());
        var existing = assignments.findBusinessAssignments(request.userIds()).stream()
                .collect(Collectors.groupingBy(link -> link.userId));
        boolean grant = "GRANT".equals(request.action());
        var added = new ArrayList<UserRole>();
        var removed = new ArrayList<UserRole>();
        var affected = new ArrayList<UserAccount>();
        for (var user : targets) {
            var links = existing.getOrDefault(user.id, List.of());
            if (grant) {
                var current = links.stream().map(link -> link.roleId).collect(Collectors.toSet());
                var missing = request.roleIds().stream().filter(id -> !current.contains(id)).toList();
                if (!missing.isEmpty()) {
                    missing.forEach(id -> added.add(assignment(user.id, id)));
                    affected.add(user);
                }
            } else {
                var revoked = links.stream().filter(link -> selected.contains(link.roleId)).toList();
                if (!revoked.isEmpty()) {
                    removed.addAll(revoked);
                    affected.add(user);
                }
            }
        }

        assignments.deleteAll(removed);
        assignments.saveAll(added);
        if (!affected.isEmpty()) {
            var action = grant ? "批量分配角色" : "批量撤销角色";
            var detail = "所选角色：" + String.join("、", businessRoles.stream().map(role -> role.name).sorted().toList());
            operationLogs.recordUserChanges(actor, action, detail, affected);
        }
        return new BatchRoleResult(request.action(), affected.size());
    }

    private List<UserAccount> lockTargets(SessionUser actor, List<Long> userIds) {
        requireAdministrator(actor);
        var targets = users.findOrganizationUsersForUpdate(userIds);
        if (targets.size() != userIds.size()) {
            throw new ManagementFailure(NOT_FOUND, "所选用户中有用户不存在。");
        }
        if (!isTotalAdmin(actor)) {
            var scope = departments.managedDepartments(actor, null).stream()
                    .map(department -> department.id).collect(Collectors.toSet());
            if (targets.stream().anyMatch(user -> !scope.contains(user.departmentId))) {
                throw new ManagementFailure(OUTSIDE_MANAGEMENT_SCOPE, "请选择本部门或下级部门内的用户。");
            }
        }
        return targets;
    }

    private List<Role> requireBusinessRoles(List<Long> roleIds) {
        var selected = roles.findAllById(roleIds);
        if (selected.size() != roleIds.size()) {
            throw new ManagementFailure(NOT_FOUND, "所选角色中有角色不存在。");
        }
        if (selected.stream().anyMatch(role -> !"BUSINESS".equals(role.roleType))) {
            throw new ManagementFailure(INVALID_REQUEST, "这里只能分配或撤销业务角色。");
        }
        return selected;
    }

    private static UserRole assignment(long userId, long roleId) {
        var assignment = new UserRole();
        assignment.userId = userId;
        assignment.roleId = roleId;
        return assignment;
    }
}
