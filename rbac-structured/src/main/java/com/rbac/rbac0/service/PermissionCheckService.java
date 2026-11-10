package com.rbac.rbac0.service;

import java.util.List;
import java.util.stream.Collectors;

import com.rbac.rbac0.dto.AuthorizationDtos.MyPermissions;
import com.rbac.rbac0.dto.AuthorizationDtos.PermissionCheck;
import com.rbac.rbac0.dto.AuthorizationDtos.PermissionCheckResult;
import com.rbac.rbac0.dto.UserDtos.RoleRef;
import com.rbac.rbac0.mapper.ApplicationMapper;
import com.rbac.rbac0.mapper.PermissionMapper;
import com.rbac.rbac0.mapper.RoleMapper;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.rbac.rbac0.service.AuthenticationFailure.Reason.UNAUTHENTICATED;
import static com.rbac.rbac0.service.ManagementFailure.Reason.APPLICATION_MISMATCH;

@Service
public class PermissionCheckService {

    private final ApplicationMapper applications;
    private final RoleMapper roles;
    private final PermissionMapper permissions;

    public PermissionCheckService(ApplicationMapper applications, RoleMapper roles, PermissionMapper permissions) {
        this.applications = applications;
        this.roles = roles;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public MyPermissions mine(long userId) {
        var assigned = roles.findAssignedRoles(List.of(userId)).stream()
                .filter(role -> "BUSINESS".equals(role.getRoleType()))
                .map(role -> new RoleRef(role.getId(), role.getName())).toList();
        var effective = permissions.findEffectiveForUser(userId).stream()
                .collect(Collectors.groupingBy(permission -> permission.applicationId));
        var trees = applications.findAll(Sort.by("id")).stream()
                .map(app -> ApplicationService.permissionTree(app, effective.getOrDefault(app.id, List.of()))).toList();
        return new MyPermissions(assigned, trees);
    }

    @Transactional(readOnly = true)
    public PermissionCheckResult check(long applicationId, String token, PermissionCheck request) {
        if (token == null || token.isBlank()) {
            throw new AuthenticationFailure(UNAUTHENTICATED);
        }
        var authenticatedApplication = applications.findIdByTokenHash(TokenHash.sha256(token))
                .orElseThrow(() -> new AuthenticationFailure(UNAUTHENTICATED));
        if (authenticatedApplication != applicationId) {
            throw new ManagementFailure(APPLICATION_MISMATCH, "应用 ID 必须与应用令牌对应。");
        }
        return new PermissionCheckResult(permissions.hasEffectivePermission(
                request.userId(), applicationId, request.permissionCode()));
    }
}
