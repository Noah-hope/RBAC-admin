package com.rbac.rbac1ddd.infrastructure.persistence;

import static java.util.stream.Collectors.toSet;

import com.rbac.rbac1ddd.domain.permission.ManagementApi;
import com.rbac.rbac1ddd.domain.permission.ManagementApiRepository;
import com.rbac.rbac1ddd.infrastructure.persistence.dao.ApiDao;
import com.rbac.rbac1ddd.infrastructure.persistence.dao.ApiPermissionDao;
import com.rbac.rbac1ddd.infrastructure.persistence.po.ApiPermissionPo;
import com.rbac.rbac1ddd.infrastructure.persistence.po.ApiPo;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 管理接口仓储的 JPA 实现：一个管理接口对应 sys_api 的一行及其 sys_api_permission 关联。 */
@Repository
@RequiredArgsConstructor
class JpaManagementApiRepository implements ManagementApiRepository {
  private final ApiDao apis;
  private final ApiPermissionDao links;

  /** 按应用编码、HTTP 方法和路径查找管理接口，并带上访问它需要的权限 ID。 */
  @Override
  public Optional<ManagementApi> findByRoute(String applicationCode, String httpMethod, String path) {
    return apis.findRoute(applicationCode, httpMethod, path)
        .map(api -> toDomain(applicationCode, api));
  }

  /** 按应用编码和操作标识查找管理接口，并带上访问它需要的权限 ID。 */
  @Override
  public Optional<ManagementApi> findByOperationId(String applicationCode, String operationId) {
    return apis.findOperation(applicationCode, operationId)
        .map(api -> toDomain(applicationCode, api));
  }

  /** 用接口行和它的权限关联重建领域对象。 */
  private ManagementApi toDomain(String applicationCode, ApiPo api) {
    return new ManagementApi(
        api.getId(),
        applicationCode,
        api.getOperationId(),
        links.findByApiId(api.getId()).stream()
            .map(ApiPermissionPo::getPermissionId)
            .collect(toSet()));
  }
}
