package com.rbac.rbac1ddd.infrastructure.persistence.dao;

import com.rbac.rbac1ddd.infrastructure.persistence.po.SessionPo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** sys_session 表的 Spring Data 访问接口，主键为令牌的 SHA-256 摘要。 */
public interface SessionDao extends JpaRepository<SessionPo, byte[]> {

  /** 删除令牌摘要对应的会话，返回删除的行数。 */
  @Modifying
  @Query("delete from SessionPo session where session.tokenHash = :tokenHash")
  int revoke(byte[] tokenHash);

  /** 删除用户的全部会话，返回删除的行数。 */
  @Modifying
  @Query("delete from SessionPo session where session.userId = :userId")
  int revokeAllOf(long userId);
}
