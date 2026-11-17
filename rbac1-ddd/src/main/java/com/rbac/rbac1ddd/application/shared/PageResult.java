package com.rbac.rbac1ddd.application.shared;

import java.util.List;

/**
 * 应用服务返回的分页结果，各列表用例共用。
 *
 * @param items 当前页的数据
 * @param page 页码，从 1 开始
 * @param pageSize 每页条数
 * @param total 符合条件的总条数
 * @param <T> 每行数据的类型
 */
public record PageResult<T>(List<T> items, int page, int pageSize, long total) {}
