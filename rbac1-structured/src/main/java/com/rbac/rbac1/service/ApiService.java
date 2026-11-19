package com.rbac.rbac1.service;

import static com.rbac.rbac1.service.DepartmentService.requireTotalAdmin;
import static com.rbac.rbac1.service.ManagementFailure.Reason.INVALID_REQUEST;
import static com.rbac.rbac1.service.ManagementFailure.Reason.NOT_FOUND;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.rbac.rbac1.dto.ApiDTO;
import com.rbac.rbac1.dto.ApiUpdateDTO;
import com.rbac.rbac1.entity.Api;
import com.rbac.rbac1.entity.ApiPermission;
import com.rbac.rbac1.entity.OperationLogTarget;
import com.rbac.rbac1.mapper.ApiMapper;
import com.rbac.rbac1.mapper.ApiPermissionMapper;
import com.rbac.rbac1.mapper.ApplicationMapper;
import com.rbac.rbac1.vo.ApiVO;
import com.rbac.rbac1.vo.PageVO;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 登记管理：总管理员为应用登记接口，并维护接口与权限的关联，供 API 调用权限检查使用。
 */
@Service
@RequiredArgsConstructor
public class ApiService {

  private final ApiMapper apis;

  private final ApiPermissionMapper links;

  private final ApplicationMapper applications;

  private final OperationLogService logs;

  /**
   * 分页查询应用登记的 API。
   *
   * @param actor 当前操作人，必须是总管理员
   * @param app 应用 ID
   * @param keyword 匹配 operationId 或路径，空串表示不过滤
   * @param page 页码，从 1 开始
   * @param pageSize 每页条数
   * @return 按 ID 排序的一页 API
   * @throws ManagementFailure 应用不存在时为 NOT_FOUND
   */
  @Transactional(readOnly = true)
  public PageVO<ApiVO> list(SessionUser actor, long app, String keyword, int page, int pageSize) {
    requireTotalAdmin(actor);
    requireApplication(app);
    Page<Api> result = apis.search(app, keyword, PageRequest.of(page - 1, pageSize));
    return new PageVO<>(result.map(this::view).toList(), page, pageSize, result.getTotalElements());
  }

  /**
   * 为应用登记 API，并记录全局操作记录。
   *
   * @param actor 当前操作人，必须是总管理员
   * @param app 应用 ID
   * @param input 接口资料
   * @return 登记后的 API
   * @throws ManagementFailure 应用不存在时为 NOT_FOUND
   */
  @Transactional
  public ApiVO create(SessionUser actor, long app, ApiDTO input) {
    requireTotalAdmin(actor);
    requireApplication(app);
    Api api = CloneFactory.copy(new Api(), input);
    api.setApplicationId(app);
    apis.save(api);
    logs.recordGlobalChange(
        actor,
        "登记API",
        api.getHttpMethod() + " " + api.getPath(),
        OperationLogTarget.API,
        api.getId(),
        api.getOperationId());
    return view(api);
  }

  /**
   * 部分修改 API 资料，只覆盖请求中给出的字段。
   *
   * @param actor 当前操作人，必须是总管理员
   * @param app 应用 ID
   * @param id API ID
   * @param input 要修改的字段，至少一项
   * @return 修改后的 API
   * @throws ManagementFailure API 不存在为 NOT_FOUND，没有任何字段为 INVALID_REQUEST
   */
  @Transactional
  public ApiVO update(SessionUser actor, long app, long id, ApiUpdateDTO input) {
    requireTotalAdmin(actor);
    Api api = requireApi(app, id);
    if (input.getOperationId() == null && input.getHttpMethod() == null && input.getPath() == null) {
      throw new ManagementFailure(INVALID_REQUEST, "请填写要修改的接口资料。");
    }
    CloneFactory.copyNotNull(api, input);
    logs.recordGlobalChange(
        actor,
        "编辑API",
        api.getHttpMethod() + " " + api.getPath(),
        OperationLogTarget.API,
        id,
        api.getOperationId());
    return view(api);
  }

  /**
   * 移除 API 登记及其全部权限关联。
   *
   * @param actor 当前操作人，必须是总管理员
   * @param app 应用 ID
   * @param id API ID
   * @throws ManagementFailure API 不存在时为 NOT_FOUND
   */
  @Transactional
  public void delete(SessionUser actor, long app, long id) {
    requireTotalAdmin(actor);
    Api api = requireApi(app, id);
    links.deleteByApiId(id);
    apis.delete(api);
    logs.recordGlobalChange(
        actor, "移除API", "移除接口登记及全部权限关联", OperationLogTarget.API, id, api.getOperationId());
  }

  /**
   * 列出与某权限关联的 API。
   *
   * @param id 权限 ID
   * @return 关联的 API
   */
  public List<ApiVO> forPermission(long id) {
    return apis
        .findAllById(links.findByPermissionId(id).stream().map(l -> l.getApiId()).toList())
        .stream()
        .map(this::view)
        .toList();
  }

  /**
   * 把权限关联的 API 替换为给定集合，只增删发生变化的关联。
   *
   * <p>在调用方的权限保存事务内执行。
   *
   * @param app 权限所属应用 ID
   * @param permission 权限 ID
   * @param ids 新的 API ID 集合
   * @throws ManagementFailure 有 API 不存在或不属于该应用时为 INVALID_REQUEST
   */
  public void replaceLinks(long app, long permission, List<Long> ids) {
    List<Api> selected = apis.findAllById(ids);
    if (selected.size() != ids.size() || selected.stream().anyMatch(a -> a.getApplicationId() != app)) {
      throw new ManagementFailure(INVALID_REQUEST, "请选择同一应用下已登记的API。");
    }
    List<ApiPermission> old = links.findByPermissionId(permission);
    Set<Long> existing = old.stream().map(l -> l.getApiId()).collect(Collectors.toSet());
    links.deleteAll(old.stream().filter(l -> !ids.contains(l.getApiId())).toList());
    links.saveAll(
        ids.stream()
            .filter(id -> !existing.contains(id))
            .map(
                id -> {
                  ApiPermission l = new ApiPermission();
                  l.setApiId(id);
                  l.setPermissionId(permission);
                  return l;
                })
            .toList());
  }

  /** 按应用和 ID 查找 API，不存在或不属于该应用时抛出 NOT_FOUND。 */
  private Api requireApi(long app, long id) {
    return apis.findByIdAndApplicationId(id, app)
        .orElseThrow(() -> new ManagementFailure(NOT_FOUND, "API不存在。"));
  }

  /** 要求应用存在。 */
  private void requireApplication(long id) {
    if (!applications.existsById(id)) {
      throw new ManagementFailure(NOT_FOUND, "应用不存在。");
    }
  }

  /** 组装 API 响应，关联权限 ID 按升序返回。 */
  private ApiVO view(Api a) {
    return new ApiVO(
        a.getId(),
        a.getApplicationId(),
        a.getOperationId(),
        a.getHttpMethod(),
        a.getPath(),
        links.findByApiId(a.getId()).stream().map(l -> l.getPermissionId()).sorted().toList());
  }
}
