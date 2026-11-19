package com.rbac.rbac1.vo;

/**
 * 接口权限检查的响应体。
 *
 * @param operationId 被检查接口的操作标识
 * @param allowed 可以调用时为 true
 */
public record ApiCheckVO(String operationId, boolean allowed) {}
