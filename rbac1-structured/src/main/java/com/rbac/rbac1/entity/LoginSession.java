package com.rbac.rbac1.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** sys_session 表的一行：一次登录会话，只保存令牌的 SHA-256 摘要。 */
@Entity
@Table(name = "sys_session")
@Getter
@Setter
@NoArgsConstructor
public class LoginSession {

  @Id
  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(length = 32)
  private byte[] tokenHash;

  @Column(nullable = false)
  private Long userId;

  @Column(nullable = false)
  private LocalDateTime expiresAt;
}
