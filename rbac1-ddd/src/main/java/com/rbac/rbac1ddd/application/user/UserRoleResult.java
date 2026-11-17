package com.rbac.rbac1ddd.application.user;

import java.util.List;

/**
 * 替换用户角色后的结果。
 *
 * @param userId 用户 ID
 * @param roleIds 提交的业务角色 ID，升序
 */
public record UserRoleResult(long userId, List<Long> roleIds) {}
