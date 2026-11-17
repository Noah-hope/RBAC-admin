package com.rbac.rbac1ddd.application.app;

import java.util.List;

/**
 * 已登记接口及其关联权限（ApiView）。
 *
 * @param id 接口 ID
 * @param applicationId 所属应用 ID
 * @param operationId operationId
 * @param httpMethod 请求方法
 * @param path 路由模板
 * @param permissionIds 关联该接口的权限 ID，升序
 */
public record ApiResult(
    long id,
    long applicationId,
    String operationId,
    String httpMethod,
    String path,
    List<Long> permissionIds) {}
