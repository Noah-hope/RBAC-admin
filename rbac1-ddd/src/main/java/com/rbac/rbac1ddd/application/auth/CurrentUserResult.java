package com.rbac.rbac1ddd.application.auth;

import java.util.List;

/**
 * 当前用户在目标应用中的身份和权限（AuthUser）。
 *
 * @param subject 用户 ID 的字符串形式
 * @param roles 目标应用的映射角色码，未启用映射时为有有效权限的直接角色 ID 字符串
 * @param permissions 目标应用内去重的有效权限编码，包含继承来源
 * @param name 姓名
 * @param no 登录账号
 * @param id 用户 ID
 * @param departmentId 所属部门 ID，可能为空
 * @param managementRole 管理身份名称
 */
public record CurrentUserResult(
    String subject,
    List<String> roles,
    List<String> permissions,
    String name,
    String no,
    long id,
    Long departmentId,
    String managementRole) {}
