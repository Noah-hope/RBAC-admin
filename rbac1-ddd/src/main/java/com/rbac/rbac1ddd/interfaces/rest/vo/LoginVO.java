package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.auth.LoginResult;

/**
 * 登录成功的响应体（LoginResult）。
 *
 * @param accessToken 访问令牌
 * @param tokenType 令牌类型，固定为 Bearer
 * @param expiresIn 有效期秒数
 */
public record LoginVO(String accessToken, String tokenType, long expiresIn) {

  /** 由应用层结果逐字段转换。 */
  public static LoginVO from(LoginResult result) {
    return new LoginVO(result.accessToken(), result.tokenType(), result.expiresIn());
  }
}
