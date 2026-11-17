package com.rbac.rbac1ddd.application.user;

import com.rbac.rbac1ddd.application.role.RoleRefResult;
import com.rbac.rbac1ddd.domain.user.UserStatus;
import java.util.List;

/**
 * 用户详情（UserView），不含密码摘要。
 *
 * @param id 用户 ID
 * @param username 登录账号
 * @param name 姓名
 * @param department 所属部门
 * @param status 账号状态
 * @param departmentAdministrator 是否持有部门管理员角色
 * @param businessRoles 直接持有的业务角色，按角色 ID 升序
 */
public record UserResult(
    long id,
    String username,
    String name,
    DepartmentRefResult department,
    UserStatus status,
    boolean departmentAdministrator,
    List<RoleRefResult> businessRoles) {}
