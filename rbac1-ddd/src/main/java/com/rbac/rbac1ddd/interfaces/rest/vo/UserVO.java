package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.user.UserResult;
import java.util.List;

/**
 * 用户详情响应体（UserView），不含任何密码信息。
 *
 * @param id 用户 ID
 * @param username 登录账号
 * @param name 姓名
 * @param department 所属部门
 * @param status 账号状态：ACTIVE 或 DISABLED
 * @param departmentAdministrator 是否持有部门管理员角色
 * @param businessRoles 直接持有的业务角色，按角色 ID 升序
 */
public record UserVO(
    long id,
    String username,
    String name,
    DepartmentRefVO department,
    String status,
    boolean departmentAdministrator,
    List<RoleRefVO> businessRoles) {

  /** 由应用层结果转换，账号状态输出枚举名。 */
  public static UserVO from(UserResult result) {
    return new UserVO(
        result.id(),
        result.username(),
        result.name(),
        DepartmentRefVO.from(result.department()),
        result.status().name(),
        result.departmentAdministrator(),
        RoleRefVO.fromAll(result.businessRoles()));
  }
}
