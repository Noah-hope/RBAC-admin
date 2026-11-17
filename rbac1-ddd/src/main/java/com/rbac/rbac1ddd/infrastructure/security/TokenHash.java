package com.rbac.rbac1ddd.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 会话表只保存令牌摘要，与 rbac1-structured 的算法一致。 */
public final class TokenHash {
  private TokenHash() {}

  /** 计算令牌 UTF-8 字节的 SHA-256 摘要（32 字节）。 */
  public static byte[] sha256(String token) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
