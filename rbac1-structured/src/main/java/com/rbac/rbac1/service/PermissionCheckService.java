package com.rbac.rbac1.service;

import com.rbac.rbac1.dto.AuthorizationDtos.*;
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
  private final RolePermissionService effective;

  public PermissionCheckService(
      ApplicationMapper applications,
      RolePermissionService effective,
      ApiMapper apis,
      ApiPermissionMapper links) {
    this.apis = apis;
    this.links = links;
    this.applications = applications;
    this.effective = effective;
  }

  @Transactional(readOnly = true)
  public MyPermissions mine(long userId) {
    var user=effective.forUser(userId); var graph=user.graph();
    var assigned=effective.references(graph,user.roleIds().stream()
        .filter(id -> "BUSINESS".equals(graph.roles().get(id).roleType)).toList());
    var trees=graph.applications().values().stream().sorted(java.util.Comparator.comparing(a -> a.id))
        .map(app -> {
          var menus=user.sources().keySet().stream().map(graph.permissions()::get)
              .filter(p -> p.applicationId.equals(app.id) && "MENU".equals(p.type))
              .map(menu -> new MyMenu(menu.id,menu.name,menu.code,
                  effective.references(graph,user.sources().get(menu.id)),
                  user.sources().keySet().stream().map(graph.permissions()::get)
                      .filter(button -> menu.id.equals(button.parentId))
                      .map(button -> new MyPermissionRef(button.id,button.name,button.code,
                          effective.references(graph,user.sources().get(button.id)))).toList())).toList();
          return new MyApplicationPermissions(app.id,app.name,app.code,menus);
        }).toList();
    return new MyPermissions(assigned,trees);
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
    var user=effective.forUser(actor.id()); var graph=user.graph();
    var granted=user.sources().keySet().stream().map(graph.permissions()::get)
        .filter(p -> p.applicationId.equals(app.id)).toList();
    var assigned=user.roleIds().stream().filter(id -> effective.effectiveIds(graph,id).stream()
        .anyMatch(permission -> graph.permissions().get(permission).applicationId.equals(app.id)))
        .map(Object::toString).toList();
    return new AuthUser(
        Long.toString(actor.id()),
        assigned,
        granted.stream().map(p -> p.code).distinct().toList(),
        actor.name(),
        actor.username(),
        actor.id(),
        actor.departmentId(),
        actor.managementRole());
  }

  @Transactional(readOnly = true)
  public PermissionCheckResult checkPermission(long user, String app, String code) {
    var application=requireApplication(app);
    var granted=effective.forUser(user);
    return new PermissionCheckResult(granted.sources().keySet().stream()
        .map(granted.graph().permissions()::get)
        .anyMatch(p -> p.applicationId.equals(application.id) && p.code.equals(code)));
  }

  @Transactional(readOnly = true)
  public ApiCheckResult checkApi(long user, String app, String operation) {
    var api =
        apis.findByApplicationIdAndOperationId(requireApplication(app).id, operation)
            .orElseThrow(
                () -> new ManagementFailure(ManagementFailure.Reason.NOT_FOUND, "API未登记。"));
    if ("permission-center".equals(app) && SELF_SERVICE.contains(operation))
      return new ApiCheckResult(operation, true);
    var permissionIds = effective.forUser(user).sources().keySet();
    return new ApiCheckResult(
        operation,
        links.findByApiId(api.id).stream().anyMatch(l -> permissionIds.contains(l.permissionId)));
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
