package com.rbac.rbac1ddd.infrastructure.persistence;

import com.rbac.rbac1ddd.application.auth.SessionStore;
import com.rbac.rbac1ddd.infrastructure.persistence.dao.SessionDao;
import com.rbac.rbac1ddd.infrastructure.persistence.po.SessionPo;
import com.rbac.rbac1ddd.infrastructure.security.TokenHash;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 会话存储端口的 JPA 实现：sys_session 只保存令牌的 SHA-256 摘要，过期时间按 UTC 保存。 */
@Repository
@RequiredArgsConstructor
class JpaSessionStore implements SessionStore {
  private final SessionDao sessions;

  /** 保存令牌摘要、用户 ID 和 UTC 过期时间。 */
  @Override
  public void open(String token, long userId, Instant expiresAt) {
    sessions.save(
        new SessionPo(
            TokenHash.sha256(token), userId, LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC)));
  }

  /** 按令牌的 SHA-256 摘要查找会话，只返回尚未过期（按 UTC 比较）会话的用户 ID。 */
  @Override
  public Optional<Long> findActiveUserId(String token) {
    return sessions
        .findById(TokenHash.sha256(token))
        .filter(session -> session.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)))
        .map(SessionPo::getUserId);
  }

  /** 删除令牌摘要对应的会话。 */
  @Override
  public boolean revoke(String token) {
    return sessions.revoke(TokenHash.sha256(token)) > 0;
  }

  /** 删除用户的全部会话。 */
  @Override
  public void revokeAllOf(long userId) {
    sessions.revokeAllOf(userId);
  }
}
