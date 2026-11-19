package com.rbac.rbac1.vo;

/**
 * 批量分配或撤销业务角色的结果。
 *
 * @param action GRANT 或 REVOKE
 * @param affectedUsers 角色实际发生变化的用户数
 */
public record BatchRoleVO(String action, int affectedUsers) {}
