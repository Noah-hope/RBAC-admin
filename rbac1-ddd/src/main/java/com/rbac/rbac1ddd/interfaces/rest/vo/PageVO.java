package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.shared.PageResult;
import java.util.List;
import java.util.function.Function;

/**
 * 分页响应体，字段与 rbac1-structured 的各类 Page 相同。
 *
 * @param items 当前页的数据
 * @param page 页码，从 1 开始
 * @param pageSize 每页条数
 * @param total 符合条件的总条数
 * @param <T> 每行数据的响应类型
 */
public record PageVO<T>(List<T> items, int page, int pageSize, long total) {

  /**
   * 把应用层分页结果转换为响应体，逐行用给定方法转换。
   *
   * @param result 应用层分页结果
   * @param mapper 每行 Result → VO 的转换方法
   * @param <R> 每行数据的应用层类型
   * @param <T> 每行数据的响应类型
   * @return 分页响应体
   */
  public static <R, T> PageVO<T> from(PageResult<R> result, Function<R, T> mapper) {
    return new PageVO<>(
        result.items().stream().map(mapper).toList(),
        result.page(),
        result.pageSize(),
        result.total());
  }
}
