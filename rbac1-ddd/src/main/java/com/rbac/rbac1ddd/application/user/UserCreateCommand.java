package com.rbac.rbac1ddd.application.user;

import com.rbac.rbac1ddd.domain.user.UserStatus;

/**
 * 新增用户的命令。
 *
 * @param username 登录账号
 * @param initialPassword 初始密码明文，只用于计算摘要，不保存也不返回
 * @param name 姓名
 * @param departmentId 所属部门 ID
 * @param status 初始账号状态
 * @param departmentAdministrator 是否同时任命为部门管理员，null 表示未填写
 */
public record UserCreateCommand(
    String username,
    String initialPassword,
    String name,
    long departmentId,
    UserStatus status,
    Boolean departmentAdministrator) {}
