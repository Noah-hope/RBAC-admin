package com.rbac.rbac1ddd.infrastructure.persistence.po;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 持久化对象：sys_user_role 表的一行，表示用户被分配的一个角色。 */
@Entity
@Table(name = "sys_user_role")
@IdClass(UserRolePo.Key.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserRolePo {
  @Id
  private Long userId;

  @Id
  private Long roleId;

  /** 复合主键：用户 ID 与角色 ID。 */
  public record Key(Long userId, Long roleId) implements Serializable {}
}
