package com.rbac.rbac1ddd.application.auth;

/** 校验权限中心签发的 JWT；校验失败抛出 {@link AuthenticationFailure}。 */
public interface TokenVerifier {
  /**
   * 校验令牌的签名和签发者。
   *
   * @param token 原始 JWT 字符串
   * @return 校验通过的令牌内容
   */
  VerifiedToken verify(String token);
}
