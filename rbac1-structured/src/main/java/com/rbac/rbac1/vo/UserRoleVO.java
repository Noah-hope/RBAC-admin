package com.rbac.rbac1.vo;

import java.util.List;

/**
 * 替换用户业务角色后的响应体。
 *
 * @param userId 用户 ID
 * @param roleIds 用户现有的业务角色 ID
 */
public record UserRoleVO(long userId, List<Long> roleIds) {}
