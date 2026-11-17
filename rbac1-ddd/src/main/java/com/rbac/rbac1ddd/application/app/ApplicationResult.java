package com.rbac.rbac1ddd.application.app;

import com.rbac.rbac1ddd.domain.app.BusinessApplication;

/**
 * 应用查询、新增或改名后的结果（ApplicationView）。
 *
 * @param id 应用 ID
 * @param name 应用名称
 * @param code 应用编码
 */
public record ApplicationResult(long id, String name, String code) {

  /** 由应用聚合逐字段转换。 */
  static ApplicationResult of(BusinessApplication application) {
    return new ApplicationResult(application.getId(), application.getName(), application.getCode());
  }
}
