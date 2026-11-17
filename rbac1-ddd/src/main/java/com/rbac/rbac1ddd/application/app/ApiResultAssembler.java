package com.rbac.rbac1ddd.application.app;

import com.rbac.rbac1ddd.domain.api.Api;
import com.rbac.rbac1ddd.domain.permission.PermissionDefinition;
import com.rbac.rbac1ddd.domain.permission.PermissionRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 把接口聚合组装为带关联权限 ID 的结果。
 *
 * <p>关联由权限一方维护，因此一次读出关联了这些接口的权限，再按接口分组，避免逐个接口查询。
 */
@Component
@RequiredArgsConstructor
class ApiResultAssembler {

  private final PermissionRepository permissions;

  /**
   * 组装一组接口的结果，保持传入顺序。
   *
   * @param apis 接口聚合
   * @return 每个接口及其关联权限 ID（升序）
   */
  List<ApiResult> results(Collection<Api> apis) {
    List<Long> apiIds = apis.stream().map(Api::getId).toList();
    Map<Long, List<Long>> permissionIds = new HashMap<>();
    for (PermissionDefinition permission : permissions.findLinkedToApis(apiIds)) {
      for (Long apiId : permission.getApiIds()) {
        permissionIds.computeIfAbsent(apiId, key -> new ArrayList<>()).add(permission.getId());
      }
    }
    return apis.stream()
        .map(api -> result(api, permissionIds.getOrDefault(api.getId(), List.of())))
        .toList();
  }

  /**
   * 组装单个接口的结果。
   *
   * @param api 接口聚合
   * @return 接口及其关联权限 ID
   */
  ApiResult result(Api api) {
    return results(List.of(api)).getFirst();
  }

  /** 接口聚合 → 结果；权限仓储按 ID 升序返回，因此关联权限 ID 已是升序。 */
  private static ApiResult result(Api api, List<Long> permissionIds) {
    return new ApiResult(
        api.getId(),
        api.getApplicationId(),
        api.getOperationId(),
        api.getHttpMethod(),
        api.getPath(),
        List.copyOf(permissionIds));
  }
}
