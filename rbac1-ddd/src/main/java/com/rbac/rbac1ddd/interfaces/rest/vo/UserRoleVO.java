package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.user.UserRoleResult;
import java.util.List;

/**
 * 保存用户业务角色后的响应体。
 *
 * @param userId 用户 ID
 * @param roleIds 用户的业务角色 ID，升序
 */
public record UserRoleVO(long userId, List<Long> roleIds) {

  /** 由应用层结果逐字段转换。 */
  public static UserRoleVO from(UserRoleResult result) {
    return new UserRoleVO(result.userId(), result.roleIds());
  }
}
