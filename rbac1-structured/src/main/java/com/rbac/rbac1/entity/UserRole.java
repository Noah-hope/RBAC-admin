package com.rbac.rbac1.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** sys_user_role 表的一行：用户被分配的角色。 */
@Entity
@Table(name = "sys_user_role")
@IdClass(UserRole.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class UserRole {

  @Id
  private Long userId;

  @Id
  private Long roleId;

  /**
   * 复合主键。
   *
   * @param userId 用户 ID
   * @param roleId 角色 ID
   */
  public record Key(Long userId, Long roleId) implements Serializable {}
}
