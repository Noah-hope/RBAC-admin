package com.rbac.rbac1.service;

import com.rbac.rbac1.dto.AuthorizationDtos.MyPermissions;
import com.rbac.rbac1.dto.AuthorizationDtos.PermissionCheckResult;
import com.rbac.rbac1.dto.SessionDtos;
import com.rbac.rbac1.dto.UserDtos.RoleRef;
import com.rbac.rbac1.entity.BusinessApplication;
import com.rbac.rbac1.mapper.ApiMapper;
import com.rbac.rbac1.mapper.ApiPermissionMapper;
import com.rbac.rbac1.mapper.ApplicationMapper;
import com.rbac.rbac1.mapper.PermissionMapper;
import com.rbac.rbac1.mapper.RoleMapper;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionCheckService {

  private static final java.util.Set<String> SELF_SERVICE =
      java.util.Set.of(
          "getMyPermissions",
          "getRequestablePermissions",
          "listPermissionRequests",
          "createPermissionRequest",
          "getPermissionRequest",
          "withdrawPermissionRequest");

  private final ApiMapper apis;
  private final ApiPermissionMapper links;
  private final ApplicationMapper applications;
  private final RoleMapper roles;
  private final PermissionMapper permissions;

  public PermissionCheckService(
      ApplicationMapper applications,
      RoleMapper roles,
      PermissionMapper permissions,
      ApiMapper apis,
      ApiPermissionMapper links) {
    this.apis = apis;
    this.links = links;
    this.applications = applications;
    this.roles = roles;
    this.permissions = permissions;
  }

  @Transactional(readOnly = true)
  public MyPermissions mine(long userId) {
    var assigned =
        roles.findAssignedRoles(List.of(userId)).stream()
            .filter(role -> "BUSINESS".equals(role.getRoleType()))
            .map(role -> new RoleRef(role.getId(), role.getName()))
            .toList();
    var effective =
        permissions.findEffectiveForUser(userId).stream()
            .collect(Collectors.groupingBy(permission -> permission.applicationId));
    var trees =
        applications.findAll(Sort.by("id")).stream()
            .map(
                app ->
                    ApplicationService.permissionTree(
                        app, effective.getOrDefault(app.id, List.of())))
            .toList();
    return new MyPermissions(assigned, trees);
  }

  public record ApiCheckResult(String operationId, boolean allowed) {}

  public record AuthUser(
      String subject,
      List<String> roles,
      List<String> permissions,
      String name,
      String no,
      long id,
      Long departmentId,
      String managementRole) {}

  @Transactional(readOnly = true)
  public AuthUser current(SessionDtos.SessionUser actor, String code) {
    var app = requireApplication(code);
    var effective =
        permissions.findEffectiveForUser(actor.id()).stream()
            .filter(p -> p.applicationId.equals(app.id))
            .toList();
    var assigned =
        roles.findAssignedRoleIdsForApplication(actor.id(), app.id).stream()
            .map(Object::toString)
            .toList();
    return new AuthUser(
        Long.toString(actor.id()),
        assigned,
        effective.stream().map(p -> p.code).distinct().toList(),
        actor.name(),
        actor.username(),
        actor.id(),
        actor.departmentId(),
        actor.managementRole());
  }

  @Transactional(readOnly = true)
  public PermissionCheckResult checkPermission(long user, String app, String code) {
    return new PermissionCheckResult(
        permissions.hasEffectivePermission(user, requireApplication(app).id, code));
  }

  @Transactional(readOnly = true)
  public ApiCheckResult checkApi(long user, String app, String operation) {
    var api =
        apis.findByApplicationIdAndOperationId(requireApplication(app).id, operation)
            .orElseThrow(
                () -> new ManagementFailure(ManagementFailure.Reason.NOT_FOUND, "API未登记。"));
    if ("permission-center".equals(app) && SELF_SERVICE.contains(operation))
      return new ApiCheckResult(operation, true);
    var effective =
        permissions.findEffectiveForUser(user).stream().map(p -> p.id).collect(Collectors.toSet());
    return new ApiCheckResult(
        operation,
        links.findByApiId(api.id).stream().anyMatch(l -> effective.contains(l.permissionId)));
  }

  @Transactional(readOnly = true)
  public void requireManagementApi(long user, String method, String path) {
    var app = requireApplication("permission-center");
    var api =
        apis.findByApplicationIdAndHttpMethodAndPath(app.id, method, path)
            .orElseThrow(
                () -> new ManagementFailure(ManagementFailure.Reason.FORBIDDEN, "管理API未登记。"));

    if (!checkApi(user, app.code, api.operationId).allowed())
      throw new ManagementFailure(ManagementFailure.Reason.FORBIDDEN, "没有该接口的调用权限。");
  }

  private BusinessApplication requireApplication(String code) {
    return applications
        .findByCode(code)
        .orElseThrow(() -> new ManagementFailure(ManagementFailure.Reason.NOT_FOUND, "应用不存在。"));
  }
}
