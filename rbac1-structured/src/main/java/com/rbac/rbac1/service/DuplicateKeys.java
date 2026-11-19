package com.rbac.rbac1.service;

import static com.rbac.rbac1.service.ManagementFailure.Reason.DUPLICATE;

import java.sql.SQLException;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 唯一约束冲突的就地翻译：由触发保存的 Service 方法调用，把指定唯一索引的冲突转换为业务错误。
 *
 * <p>约束名按不区分大小写匹配，因为 H2 报告大写名称、MySQL 报告小写名称。其他完整性错误原样抛出，
 * 由统一异常处理兜底。
 */
final class DuplicateKeys {

  private static final int MYSQL_DUPLICATE_ENTRY = 1062;

  private static final String SQL_STATE_UNIQUE_VIOLATION = "23505";

  private DuplicateKeys() {}

  /**
   * 立即保存实体；若违反指定唯一索引，抛出「数据已存在」业务错误。
   *
   * <p>使用 saveAndFlush 让冲突在当前方法内暴露，而不是推迟到事务提交。
   *
   * @param repository 实体所属的 Mapper
   * @param entity 新建或已修改的实体
   * @param constraint 数据库唯一索引名，如 uq_role_name
   * @param detail 冲突时返回给前端的中文提示
   * @param <T> 实体类型
   * @return 保存后的实体
   */
  static <T> T saveAndFlush(
      JpaRepository<T, ?> repository, T entity, String constraint, String detail) {
    try {
      return repository.saveAndFlush(entity);
    } catch (DataIntegrityViolationException error) {
      if (violates(error, constraint)) {
        throw new ManagementFailure(DUPLICATE, detail);
      }
      throw error;
    }
  }

  /** 判断异常是否由指定唯一索引引起。 */
  private static boolean violates(DataIntegrityViolationException error, String constraint) {
    return error.getMostSpecificCause() instanceof SQLException sql
        && (sql.getErrorCode() == MYSQL_DUPLICATE_ENTRY
            || SQL_STATE_UNIQUE_VIOLATION.equals(sql.getSQLState()))
        && sql.getMessage().toLowerCase(Locale.ROOT).contains(constraint.toLowerCase(Locale.ROOT));
  }
}
