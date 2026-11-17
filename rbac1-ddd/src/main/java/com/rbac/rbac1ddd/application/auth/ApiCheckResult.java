package com.rbac.rbac1ddd.application.auth;

/**
 * 后端接口权限检查的结果。
 *
 * @param operationId 被检查接口的操作标识
 * @param allowed 当前用户可以调用该接口时为 true
 */
public record ApiCheckResult(String operationId, boolean allowed) {}
