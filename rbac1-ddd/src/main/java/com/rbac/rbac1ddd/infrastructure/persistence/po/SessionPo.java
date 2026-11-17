package com.rbac.rbac1ddd.infrastructure.persistence.po;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 持久化对象：sys_session 表的一行，表示一次登录会话，只保存令牌的 SHA-256 摘要。 */
@Entity
@Table(name = "sys_session")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SessionPo {
  /** 令牌的 SHA-256 摘要（32 字节），不保存令牌原文。 */
  @Id
  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(length = 32)
  private byte[] tokenHash;

  @Column(nullable = false)
  private Long userId;

  /** 会话过期时间（UTC）。 */
  @Column(nullable = false)
  private LocalDateTime expiresAt;
}
