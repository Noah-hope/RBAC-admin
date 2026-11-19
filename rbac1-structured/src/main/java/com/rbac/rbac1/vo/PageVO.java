package com.rbac.rbac1.vo;

import java.util.List;

/**
 * 分页列表的响应体。
 *
 * @param items 当前页的记录
 * @param page 页码，从 1 开始
 * @param pageSize 每页条数
 * @param total 符合条件的记录总数
 * @param <T> 记录类型
 */
public record PageVO<T>(List<T> items, int page, int pageSize, long total) {}
