package com.rbac.rbac1ddd.infrastructure.persistence;

import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 仓储适配器共用的唯一约束识别工具：按约束名判断数据库错误属于哪一个唯一约束。
 *
 * <p>MySQL 的错误信息形如 {@code Duplicate entry ... for key 'sys_api.uq_api_route'}，H2 的错误信息带大写的索引名，
 * 因此统一转为小写后按约束名匹配。
 */
final class UniqueConstraints {

  /** 应用编码、接口标识或路由重复时的统一提示，与 rbac1-structured 的兜底提示相同。 */
  static final String GENERIC_DUPLICATE = "名称、应用标识、接口标识或路由已存在。";

  private UniqueConstraints() {}

  /**
   * 判断数据库错误是否由指定唯一约束引起。
   *
   * @param error 保存时抛出的完整性错误
   * @param constraintName 约束名，与 V1__init_rbac1.sql 一致
   * @return 错误信息中包含该约束名时为 true
   */
  static boolean violated(DataIntegrityViolationException error, String constraintName) {
    return String.valueOf(error.getMostSpecificCause().getMessage())
        .toLowerCase(Locale.ROOT)
        .contains(constraintName);
  }
}
