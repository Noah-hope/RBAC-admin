package com.rbac.rbac1ddd.application.auth;

import java.time.Instant;
import java.util.Optional;

/** 登录会话存储端口。实现只保存令牌的摘要，不保存令牌原文。 */
public interface SessionStore {

  /**
   * 登录成功后保存会话。
   *
   * @param token 签发的 JWT
   * @param userId 登录用户 ID
   * @param expiresAt 会话过期时间，与令牌的 exp 相同
   */
  void open(String token, long userId, Instant expiresAt);

  /**
   * 查找令牌对应的未过期会话。
   *
   * @param token 原始 JWT 字符串
   * @return 会话所属用户的 ID；会话不存在或已过期时为空
   */
  Optional<Long> findActiveUserId(String token);

  /**
   * 撤销令牌对应的会话，同一用户的其他会话不受影响。
   *
   * @param token 原始 JWT 字符串
   * @return 会话存在并被删除时为 true
   */
  boolean revoke(String token);

  /**
   * 撤销用户的全部会话，用于停用账号后让其所有令牌立即失效。
   *
   * @param userId 用户 ID
   */
  void revokeAllOf(long userId);
}
