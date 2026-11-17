package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.app.ApiResult;
import java.util.List;

/**
 * 已登记接口的响应体（ApiView）。
 *
 * @param id 接口 ID
 * @param applicationId 所属应用 ID
 * @param operationId operationId
 * @param httpMethod 请求方法
 * @param path 路由模板
 * @param permissionIds 关联该接口的权限 ID，升序
 */
public record ApiVO(
    long id,
    long applicationId,
    String operationId,
    String httpMethod,
    String path,
    List<Long> permissionIds) {

  /** 由应用层结果逐字段转换。 */
  public static ApiVO from(ApiResult result) {
    return new ApiVO(
        result.id(),
        result.applicationId(),
        result.operationId(),
        result.httpMethod(),
        result.path(),
        result.permissionIds());
  }
}
