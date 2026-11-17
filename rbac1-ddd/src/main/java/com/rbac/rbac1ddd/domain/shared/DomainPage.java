package com.rbac.rbac1ddd.domain.shared;

import java.util.List;

/**
 * 领域对象的分页结果，供仓储接口返回，避免把 Spring Data 的 Page 暴露给领域层和应用层。
 *
 * @param items 当前页的领域对象
 * @param total 符合条件的总数
 * @param <T> 领域对象类型
 */
public record DomainPage<T>(List<T> items, long total) {

  /** 复制列表，保证分页结果不可修改。 */
  public DomainPage {
    items = List.copyOf(items);
  }
}
