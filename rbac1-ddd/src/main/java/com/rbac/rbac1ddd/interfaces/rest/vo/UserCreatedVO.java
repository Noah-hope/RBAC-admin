package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.user.UserResult;

/**
 * 新增用户后的响应体（UserCreated）：只包含用户详情，不回显初始密码。
 *
 * @param user 新用户的详情
 */
public record UserCreatedVO(UserVO user) {

  /** 由应用层结果转换。 */
  public static UserCreatedVO from(UserResult result) {
    return new UserCreatedVO(UserVO.from(result));
  }
}
