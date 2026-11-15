package com.rbac.rbac1.service;

import static com.rbac.rbac1.service.DepartmentService.requireTotalAdmin;
import static com.rbac.rbac1.service.ManagementFailure.Reason.*;

import com.rbac.rbac1.dto.ApiDtos.*;
import com.rbac.rbac1.dto.SessionDtos.SessionUser;
import com.rbac.rbac1.entity.*;
import com.rbac.rbac1.mapper.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiService {
  private final ApiMapper apis;
  private final ApiPermissionMapper links;
  private final ApplicationMapper applications;
  private final OperationLogService logs;

  public ApiService(
      ApiMapper apis,
      ApiPermissionMapper links,
      ApplicationMapper applications,
      OperationLogService logs) {
    this.apis = apis;
    this.links = links;
    this.applications = applications;
    this.logs = logs;
  }

  @Transactional(readOnly = true)
  public ApiPage list(SessionUser actor, long app, String keyword, int page, int pageSize) {
    requireTotalAdmin(actor);
    requireApplication(app);
    var result = apis.search(app, keyword, PageRequest.of(page - 1, pageSize));
    return new ApiPage(result.map(this::view).toList(), page, pageSize, result.getTotalElements());
  }

  @Transactional
  public ApiView create(SessionUser actor, long app, ApiInput input) {
    requireTotalAdmin(actor);
    requireApplication(app);
    var api = new Api();
    api.applicationId = app;
    api.operationId = input.operationId();
    api.httpMethod = input.httpMethod();
    api.path = input.path();
    apis.save(api);
    logs.recordGlobalChange(
        actor, "登记API", api.httpMethod + " " + api.path, "API", api.id, api.operationId);
    return view(api);
  }

  @Transactional
  public ApiView update(SessionUser actor, long app, long id, ApiUpdate input) {
    requireTotalAdmin(actor);
    var api = requireApi(app, id);
    if (input.operationId == null && input.httpMethod == null && input.path == null)
      throw new ManagementFailure(INVALID_REQUEST, "请填写要修改的接口资料。");
    if (input.operationId != null) api.operationId = input.operationId;
    if (input.httpMethod != null) api.httpMethod = input.httpMethod;
    if (input.path != null) api.path = input.path;
    logs.recordGlobalChange(
        actor, "编辑API", api.httpMethod + " " + api.path, "API", id, api.operationId);
    return view(api);
  }

  @Transactional
  public void delete(SessionUser actor, long app, long id) {
    requireTotalAdmin(actor);
    var api = requireApi(app, id);
    links.deleteByApiId(id);
    links.flush();
    apis.delete(api);
    logs.recordGlobalChange(actor, "移除API", "移除接口登记及全部权限关联", "API", id, api.operationId);
  }

  public List<ApiView> forPermission(long id) {
    return apis
        .findAllById(links.findByPermissionId(id).stream().map(l -> l.apiId).toList())
        .stream()
        .map(this::view)
        .toList();
  }

  public void replaceLinks(long app, long permission, List<Long> ids) {
    var selected = apis.findAllById(ids);
    if (selected.size() != ids.size() || selected.stream().anyMatch(a -> a.applicationId != app))
      throw new ManagementFailure(INVALID_REQUEST, "请选择同一应用下已登记的API。");
    var old = links.findByPermissionId(permission);
    var existing = old.stream().map(l -> l.apiId).collect(Collectors.toSet());
    links.deleteAll(old.stream().filter(l -> !ids.contains(l.apiId)).toList());
    links.saveAll(
        ids.stream()
            .filter(id -> !existing.contains(id))
            .map(
                id -> {
                  var l = new ApiPermission();
                  l.apiId = id;
                  l.permissionId = permission;
                  return l;
                })
            .toList());
  }

  private Api requireApi(long app, long id) {
    return apis.findByIdAndApplicationId(id, app)
        .orElseThrow(() -> new ManagementFailure(NOT_FOUND, "API不存在。"));
  }

  private void requireApplication(long id) {
    if (!applications.existsById(id)) throw new ManagementFailure(NOT_FOUND, "应用不存在。");
  }

  private ApiView view(Api a) {
    return new ApiView(
        a.id,
        a.applicationId,
        a.operationId,
        a.httpMethod,
        a.path,
        links.findByApiId(a.id).stream().map(l -> l.permissionId).sorted().toList());
  }
}
