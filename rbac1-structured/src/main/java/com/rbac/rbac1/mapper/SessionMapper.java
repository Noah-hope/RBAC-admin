package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.LoginSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/** sys_session 表的数据访问接口：以令牌摘要为主键保存和撤销登录会话。 */
public interface SessionMapper extends JpaRepository<LoginSession, byte[]> {

  /**
   * 按令牌摘要撤销一次会话，用于退出登录。
   *
   * @param tokenHash 令牌的 SHA-256 摘要
   * @return 删除的会话数，0 表示会话已失效或不存在
   */
  @Modifying
  @Query("delete from LoginSession session where session.tokenHash = :tokenHash")
  int revoke(byte[] tokenHash);

  /**
   * 撤销用户的全部会话，用于停用账号后让已签发的令牌立即失效。
   *
   * @param userId 用户 ID
   */
  @Modifying
  @Query("delete from LoginSession session where session.userId = :userId")
  void revokeAllForUser(long userId);
}
