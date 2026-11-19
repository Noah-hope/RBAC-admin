package com.rbac.rbac1.vo;

import java.util.List;

/**
 * 用户的响应体。
 *
 * @param id 用户 ID
 * @param username 登录账号
 * @param name 姓名
 * @param department 所属部门
 * @param status 账号状态
 * @param departmentAdministrator 是否为部门管理员
 * @param businessRoles 直接分配的业务角色
 */
public record UserVO(
    long id,
    String username,
    String name,
    DepartmentRefVO department,
    String status,
    boolean departmentAdministrator,
    List<RoleRefVO> businessRoles) {}
