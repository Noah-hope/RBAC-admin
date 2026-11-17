package com.rbac.rbac1ddd.domain.permission;

import java.util.Optional;

/** 已登记接口的仓储接口，供权限检查找到接口及其关联权限。 */
public interface ManagementApiRepository {

  /**
   * 按应用标识、HTTP 方法和路由模板查找已登记的接口。
   *
   * @param applicationCode 应用标识
   * @param httpMethod HTTP 方法，例如 GET、POST
   * @param path 路由模板，例如 /roles/{id}
   * @return 未登记时为空
   */
  Optional<ManagementApi> findByRoute(String applicationCode, String httpMethod, String path);

  /**
   * 按应用标识和操作标识查找已登记的接口，供业务系统检查接口权限。
   *
   * @param applicationCode 应用标识
   * @param operationId 接口的操作标识
   * @return 未登记时为空
   */
  Optional<ManagementApi> findByOperationId(String applicationCode, String operationId);
}
