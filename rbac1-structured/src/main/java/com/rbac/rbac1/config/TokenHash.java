package com.rbac.rbac1.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 令牌摘要：会话表只保存访问令牌的 SHA-256 摘要。 */
public final class TokenHash {

  /** 工具类不允许实例化。 */
  private TokenHash() {}

  /**
   * 计算令牌的 SHA-256 摘要。
   *
   * @param token 访问令牌原文
   * @return 32 字节摘要
   */
  public static byte[] sha256(String token) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
