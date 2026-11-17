package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.auth.ApiCheckResult;

/**
 * 接口权限检查的响应体（ApiCheckResult）。
 *
 * @param operationId 被检查接口的操作标识
 * @param allowed 可以调用时为 true
 */
public record ApiCheckVO(String operationId, boolean allowed) {

  /** 由应用层结果逐字段转换。 */
  public static ApiCheckVO from(ApiCheckResult result) {
    return new ApiCheckVO(result.operationId(), result.allowed());
  }
}
