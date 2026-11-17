package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.auth.CurrentUserResult;
import java.util.List;

/**
 * 当前用户信息（AuthUser），字段及顺序与 rbac1-structured 相同。
 *
 * @param subject 用户 ID 的字符串形式
 * @param roles 为目标应用提供有效权限的直接角色 ID 字符串
 * @param permissions 目标应用内去重的有效权限编码
 * @param name 姓名
 * @param no 登录账号
 * @param id 用户 ID
 * @param departmentId 所属部门 ID，可能为空
 * @param managementRole 管理身份：TOTAL_ADMIN、DEPARTMENT_ADMIN 或 NONE
 */
public record CurrentUserVO(
    String subject,
    List<String> roles,
    List<String> permissions,
    String name,
    String no,
    long id,
    Long departmentId,
    String managementRole) {

  /** 由应用层结果逐字段转换。 */
  public static CurrentUserVO from(CurrentUserResult result) {
    return new CurrentUserVO(
        result.subject(),
        result.roles(),
        result.permissions(),
        result.name(),
        result.no(),
        result.id(),
        result.departmentId(),
        result.managementRole());
  }
}
