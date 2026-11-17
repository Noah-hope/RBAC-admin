package com.rbac.rbac1ddd.application.user;

import com.rbac.rbac1ddd.domain.user.UserStatus;

/**
 * 修改用户的命令，未填写的字段为 null，表示不修改。
 *
 * @param username 新登录账号
 * @param name 新姓名
 * @param departmentId 调动后的部门 ID
 * @param status 新账号状态
 * @param departmentAdministrator 任命（true）或撤销（false）部门管理员
 */
public record UserUpdateCommand(
    String username,
    String name,
    Long departmentId,
    UserStatus status,
    Boolean departmentAdministrator) {

  /** 是否涉及调动或任免部门管理员，这两类修改仅限总管理员；填写了即算，即使与当前值相同。 */
  boolean changesOrganization() {
    return departmentId != null || departmentAdministrator != null;
  }

  /** 是否没有填写任何字段。 */
  boolean isEmpty() {
    return username == null
        && name == null
        && status == null
        && departmentId == null
        && departmentAdministrator == null;
  }
}
