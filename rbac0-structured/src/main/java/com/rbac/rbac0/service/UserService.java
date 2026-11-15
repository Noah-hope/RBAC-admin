package com.rbac.rbac0.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.rbac.rbac0.dto.SessionDtos.SessionUser;
import com.rbac.rbac0.dto.UserDtos.DepartmentRef;
import com.rbac.rbac0.dto.UserDtos.RoleRef;
import com.rbac.rbac0.dto.UserDtos.UserCreate;
import com.rbac.rbac0.dto.UserDtos.UserCreated;
import com.rbac.rbac0.dto.UserDtos.UserPage;
import com.rbac.rbac0.dto.UserDtos.UserUpdate;
import com.rbac.rbac0.dto.UserDtos.UserView;
import com.rbac.rbac0.entity.Department;
import com.rbac.rbac0.entity.UserAccount;
import com.rbac.rbac0.entity.UserRole;
import com.rbac.rbac0.mapper.RoleMapper;
import com.rbac.rbac0.mapper.RoleMapper.UserRoleRow;
import com.rbac.rbac0.mapper.SessionMapper;
import com.rbac.rbac0.mapper.UserMapper;
import com.rbac.rbac0.mapper.UserRoleMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.rbac.rbac0.service.ManagementFailure.Reason.*;

@Service
public class UserService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DEPARTMENT_ADMIN = "DEPARTMENT_ADMIN";

    private final UserMapper users;
    private final DepartmentService departments;
    private final RoleMapper roles;
    private final UserRoleMapper userRoles;
    private final SessionMapper sessions;
    private final PasswordEncoder passwords;
    private final OperationLogService operationLogs;

    public UserService(UserMapper users, DepartmentService departments, RoleMapper roles, UserRoleMapper userRoles,
                       SessionMapper sessions, PasswordEncoder passwords, OperationLogService operationLogs) {
        this.users = users;
        this.departments = departments;
        this.roles = roles;
        this.userRoles = userRoles;
        this.sessions = sessions;
        this.passwords = passwords;
        this.operationLogs = operationLogs;
    }

    @Transactional(readOnly = true)
    public UserPage list(SessionUser actor, Long departmentId, String keyword, boolean includeDepartmentName,
                         int page, int pageSize) {
        var scope = departments.managedDepartments(actor, departmentId).stream()
                .collect(Collectors.toMap(department -> department.id, Function.identity()));
        if (scope.isEmpty()) {
            return new UserPage(List.of(), page, pageSize, 0);
        }

        var matches = users.search(scope.keySet(), keyword, includeDepartmentName, PageRequest.of(page - 1, pageSize));
        var ids = matches.stream().map(user -> user.id).toList();
        var assigned = ids.isEmpty() ? Map.<Long, List<UserRoleRow>>of()
                : roles.findAssignedRoles(ids).stream().collect(Collectors.groupingBy(UserRoleRow::getUserId));
        var items = matches.stream().map(user -> view(user, scope.get(user.departmentId),
                assigned.getOrDefault(user.id, List.of()))).toList();
        return new UserPage(items, page, pageSize, matches.getTotalElements());
    }

    @Transactional(readOnly = true)
    public UserView get(SessionUser actor, long id) {
        var user = requireUser(actor, id);
        var department = departments.requireManagedDepartment(actor, user.departmentId);
        return view(user, department, roles.findAssignedRoles(List.of(id)));
    }

    @Transactional
    public UserCreated create(SessionUser actor, UserCreate request) {
        var department = departments.requireManagedDepartment(actor, request.departmentId);
        requireManagementAuthority(actor, request.departmentAdministrator != null);

        var random = new byte[24];
        RANDOM.nextBytes(random);
        var initialPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        var user = new UserAccount();
        user.username = request.username;
        user.name = request.name;
        user.departmentId = department.id;
        user.status = request.status;
        user.passwordHash = passwords.encode(initialPassword);
        users.save(user);

        operationLogs.recordUserChange(actor, "新增用户", "初始状态为" + user.status, user, department.id);
        if (Boolean.TRUE.equals(request.departmentAdministrator)) {
            setDepartmentAdministrator(actor, user, true);
        }
        return new UserCreated(view(user, department, roles.findAssignedRoles(List.of(user.id))), initialPassword);
    }

    @Transactional
    public UserView update(SessionUser actor, long id, UserUpdate request) {
        DepartmentService.requireAdministrator(actor);
        var user = users.findOrganizationUsersForUpdate(List.of(id)).stream().findFirst()
                .orElseThrow(() -> new ManagementFailure(NOT_FOUND, "用户不存在。"));
        var originalDepartment = departments.requireManagedDepartment(actor, user.departmentId);
        requireManagementAuthority(actor, request.departmentId != null || request.departmentAdministrator != null);
        if (request.username == null && request.name == null && request.status == null
                && request.departmentId == null && request.departmentAdministrator == null) {
            throw new ManagementFailure(INVALID_REQUEST, "请填写要修改的用户信息。");
        }

        var department = request.departmentId == null || request.departmentId.equals(originalDepartment.id)
                ? originalDepartment : departments.requireManagedDepartment(actor, request.departmentId);
        var changes = new ArrayList<String>();
        if (request.username != null && !request.username.equals(user.username)) {
            changes.add("用户名从「" + user.username + "」改为「" + request.username + "」");
            user.username = request.username;
        }
        if (request.name != null && !request.name.equals(user.name)) {
            changes.add("姓名从「" + user.name + "」改为「" + request.name + "」");
            user.name = request.name;
        }
        if (!changes.isEmpty()) {
            operationLogs.recordUserChange(actor, "修改用户", String.join("，", changes), user, originalDepartment.id);
        }
        if (request.status != null && !request.status.equals(user.status)) {
            boolean disabled = "DISABLED".equals(request.status);
            var detail = "状态从 " + user.status + " 改为 " + request.status;
            user.status = request.status;
            if (disabled) {
                sessions.revokeAllForUser(id);
            }
            operationLogs.recordUserChange(actor, disabled ? "停用账号" : "启用账号", detail, user, originalDepartment.id);
        }
        if (!department.id.equals(originalDepartment.id)) {
            user.departmentId = department.id;
            operationLogs.recordGlobalChange(actor, "跨部门调动",
                    "从「" + originalDepartment.name + "」调至「" + department.name + "」", "USER", id, user.name);
        }
        if (request.departmentAdministrator != null) {
            setDepartmentAdministrator(actor, user, request.departmentAdministrator);
        }
        return view(user, department, roles.findAssignedRoles(List.of(id)));
    }

    private UserAccount requireUser(SessionUser actor, long id) {
        DepartmentService.requireAdministrator(actor);
        return users.findById(id).filter(user -> user.departmentId != null)
                .orElseThrow(() -> new ManagementFailure(NOT_FOUND, "用户不存在。"));
    }

    private static void requireManagementAuthority(SessionUser actor, boolean changingManagement) {
        if (changingManagement && !DepartmentService.isTotalAdmin(actor)) {
            throw new ManagementFailure(FORBIDDEN, "只有总管理员可以调动用户或任免部门管理员。");
        }
    }

    private void setDepartmentAdministrator(SessionUser actor, UserAccount user, boolean enabled) {
        var role = roles.findByRoleType(DEPARTMENT_ADMIN);
        var key = new UserRole.Key(user.id, role.id);
        if (userRoles.existsById(key) == enabled) {
            return;
        }
        if (enabled) {
            var assignment = new UserRole();
            assignment.userId = user.id;
            assignment.roleId = role.id;
            userRoles.save(assignment);
        } else {
            userRoles.deleteById(key);
        }
        var action = enabled ? "任命部门管理员" : "撤销部门管理员";
        operationLogs.recordGlobalChange(actor, action, action, "USER", user.id, user.name);
    }

    private static UserView view(UserAccount user, Department department, List<UserRoleRow> assigned) {
        var businessRoles = assigned.stream().filter(role -> "BUSINESS".equals(role.getRoleType()))
                .map(role -> new RoleRef(role.getId(), role.getName())).toList();
        var departmentAdmin = assigned.stream().anyMatch(role -> DEPARTMENT_ADMIN.equals(role.getRoleType()));
        return new UserView(user.id, user.username, user.name, new DepartmentRef(department.id, department.name),
                user.status, departmentAdmin, businessRoles);
    }
}
