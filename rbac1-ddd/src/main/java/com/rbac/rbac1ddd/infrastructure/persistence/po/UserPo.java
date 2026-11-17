package com.rbac.rbac1ddd.infrastructure.persistence.po;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.rbac.rbac1ddd.domain.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 持久化对象：sys_user 表的一行，表示一个用户账号。
 *
 * <p>{@code @CopyFrom(User.class)} 让 CloneFactory 生成 copy(UserPo, User)，拷贝同名同类型字段；
 * 账号状态在领域层是枚举、在表中是字符串，单独转换。
 */
@Entity
@Table(
    name = "sys_user",
    uniqueConstraints = @UniqueConstraint(name = "uq_user_username", columnNames = "username"))
@CopyFrom(User.class)
@Getter
@Setter
@NoArgsConstructor
public class UserPo {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 所属部门 ID，不属于任何部门的账号为 null。 */
  private Long departmentId;

  @Column(nullable = false, length = 32)
  private String username;

  @Column(nullable = false, length = 24)
  private String name;

  /** 密码的 Argon2 摘要，不保存明文。 */
  @Column(nullable = false)
  private String passwordHash;

  /** 账号状态，取值与领域层 UserStatus 枚举名一致。 */
  @Column(nullable = false, length = 8)
  @CopyFrom.Exclude(User.class)
  private String status;
}
