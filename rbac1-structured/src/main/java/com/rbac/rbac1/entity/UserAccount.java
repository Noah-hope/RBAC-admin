package com.rbac.rbac1.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** sys_user 表的一行：用户账号，用户名唯一，密码只保存 Argon2 摘要。 */
@Entity
@Table(
    name = "sys_user",
    uniqueConstraints = {
      @UniqueConstraint(name = "uq_user_username", columnNames = {"username"})
    })
@Getter
@Setter
@NoArgsConstructor
public class UserAccount {

  /** 账号状态：启用。 */
  public static final String ACTIVE = "ACTIVE";

  /** 账号状态：停用，停用时撤销全部会话。 */
  public static final String DISABLED = "DISABLED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long departmentId;

  @Column(nullable = false, length = 32)
  private String username;

  @Column(nullable = false, length = 24)
  private String name;

  @Column(nullable = false)
  private String passwordHash;

  @Column(nullable = false, length = 8)
  private String status;
}
