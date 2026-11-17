package com.rbac.rbac1ddd.application.app;

import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.INVALID_REQUEST;
import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.NOT_FOUND;

import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.application.shared.OperationRecorder;
import com.rbac.rbac1ddd.application.shared.OperationTarget;
import com.rbac.rbac1ddd.application.shared.PageResult;
import com.rbac.rbac1ddd.domain.api.Api;
import com.rbac.rbac1ddd.domain.api.ApiRepository;
import com.rbac.rbac1ddd.domain.app.BusinessApplicationRepository;
import com.rbac.rbac1ddd.domain.permission.PermissionDefinition;
import com.rbac.rbac1ddd.domain.permission.PermissionRepository;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.shared.DomainPage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用服务：应用 API 清单的查询、登记、编辑和移除，仅限总管理员。
 *
 * <p>只负责编排。operationId 和路由在应用内唯一由仓储翻译；移除接口时先让关联它的权限各自解除关联，
 * 再删除接口登记，权限本身保留。
 */
@Service
@RequiredArgsConstructor
public class ApiApplicationService {

  private final BusinessApplicationRepository applications;
  private final ApiRepository apis;
  private final PermissionRepository permissions;
  private final ApiResultAssembler apiResults;
  private final OperationRecorder recorder;

  /** 按关键字分页查询应用下的接口，按 ID 升序。 */
  @Transactional(readOnly = true)
  public PageResult<ApiResult> list(
      Operator operator, long applicationId, String keyword, int page, int pageSize) {
    operator.requireTotalAdmin();
    requireApplication(applicationId);
    DomainPage<Api> found = apis.search(applicationId, keyword, page, pageSize);
    return new PageResult<>(apiResults.results(found.items()), page, pageSize, found.total());
  }

  /** 在应用下登记接口，写入全局操作记录。 */
  @Transactional
  public ApiResult create(Operator operator, long applicationId, ApiCommand command) {
    operator.requireTotalAdmin();
    requireApplication(applicationId);
    Api api =
        Api.register(applicationId, command.operationId(), command.httpMethod(), command.path());
    apis.save(api);
    recorder.recordGlobal(operator, "登记API", api.route(), OperationTarget.registeredApi(api));
    return apiResults.result(api);
  }

  /** 修改接口资料，只修改填写的项；与 rbac1-structured 相同，每次修改都写操作记录。 */
  @Transactional
  public ApiResult update(Operator operator, long applicationId, long apiId, ApiCommand command) {
    operator.requireTotalAdmin();
    Api api = requireApi(applicationId, apiId);
    if (command.isEmpty()) throw new DomainException(INVALID_REQUEST, "请填写要修改的接口资料。");
    api.revise(command.operationId(), command.httpMethod(), command.path());
    apis.save(api);
    recorder.recordGlobal(operator, "编辑API", api.route(), OperationTarget.registeredApi(api));
    return apiResults.result(api);
  }

  /** 移除接口登记及其全部权限关联，关联的权限保留，写入全局操作记录。 */
  @Transactional
  public void delete(Operator operator, long applicationId, long apiId) {
    operator.requireTotalAdmin();
    Api api = requireApi(applicationId, apiId);
    for (PermissionDefinition permission : permissions.findLinkedToApis(List.of(apiId))) {
      permission.unlinkApi(apiId);
      permissions.save(permission);
    }
    apis.delete(api);
    recorder.recordGlobal(
        operator, "移除API", "移除接口登记及全部权限关联", OperationTarget.registeredApi(api));
  }

  /** 要求应用存在。 */
  private void requireApplication(long applicationId) {
    if (applications.findById(applicationId).isEmpty())
      throw new DomainException(NOT_FOUND, "应用不存在。");
  }

  /** 读取应用下的接口；应用或接口不存在时都提示接口不存在，与 rbac1-structured 相同。 */
  private Api requireApi(long applicationId, long apiId) {
    return apis.findInApplication(applicationId, apiId)
        .orElseThrow(() -> new DomainException(NOT_FOUND, "API不存在。"));
  }
}
