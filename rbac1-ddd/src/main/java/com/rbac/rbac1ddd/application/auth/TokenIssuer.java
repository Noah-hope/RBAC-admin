package com.rbac.rbac1ddd.application.auth;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 访问令牌签发端口：用权限中心的私钥签发 RS256 JWT，并公开对应的公钥。 */
public interface TokenIssuer {

  /**
   * 签发访问令牌。令牌只标识用户和适用应用，不携带角色和权限。
   *
   * @param userId 用户 ID，写入 sub
   * @param audience 适用的应用编码，写入 aud
   * @param expiresAt 过期时间，写入 exp
   * @return 签名后的 JWT 字符串
   */
  String issue(long userId, List<String> audience, Instant expiresAt);

  /**
   * 签名公钥的 JWK 列表，不包含任何私钥参数。
   *
   * @return 每个元素是一把公钥的 JWK 字段
   */
  List<Map<String, Object>> publicKeys();
}
