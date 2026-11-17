package com.rbac.rbac1ddd.infrastructure.persistence.po;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 持久化对象：sys_role_inheritance 表的一行，role_id 继承 inherited_role_id（子 → 父）。 */
@Entity
@Table(name = "sys_role_inheritance")
@IdClass(RoleInheritancePo.Key.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RoleInheritancePo {
  /** 子角色 ID。 */
  @Id
  private Long roleId;

  /** 被继承的父角色 ID。 */
  @Id
  private Long inheritedRoleId;

  /** 复合主键：子角色 ID 与父角色 ID。 */
  public record Key(Long roleId, Long inheritedRoleId) implements Serializable {}
}
