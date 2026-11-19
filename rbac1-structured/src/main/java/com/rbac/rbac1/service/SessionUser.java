package com.rbac.rbac1.service;

/**
 * 已认证的操作人，由会话拦截器解析令牌后交给 Service 做权限判断和日志记录。
 *
 * @param id 用户 ID
 * @param username 登录账号
 * @param name 姓名
 * @param departmentId 所属部门 ID
 * @param managementRole 管理身份：TOTAL_ADMIN、DEPARTMENT_ADMIN 或 NONE
 */
public record SessionUser(
    long id, String username, String name, Long departmentId, String managementRole) {}
