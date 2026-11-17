package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.app.ApplicationResult;

/**
 * 应用响应体（ApplicationView）。
 *
 * @param id 应用 ID
 * @param name 应用名称
 * @param code 应用编码
 */
public record ApplicationVO(long id, String name, String code) {

  /** 由应用层结果逐字段转换。 */
  public static ApplicationVO from(ApplicationResult result) {
    return new ApplicationVO(result.id(), result.name(), result.code());
  }
}
