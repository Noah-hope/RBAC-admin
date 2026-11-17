package com.rbac.rbac1ddd.application.user;

/**
 * 批量分配或撤销的结果。
 *
 * @param action 操作类型名称：GRANT 或 REVOKE
 * @param affectedUsers 角色实际发生变化的用户数
 */
public record BatchRoleResult(String action, int affectedUsers) {}
