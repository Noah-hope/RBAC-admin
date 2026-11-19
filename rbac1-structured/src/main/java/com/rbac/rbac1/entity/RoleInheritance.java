package com.rbac.rbac1.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** sys_role_inheritance 表的一行：子角色继承父角色的关系。 */
@Entity
@Table(name = "sys_role_inheritance")
@IdClass(RoleInheritance.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class RoleInheritance {

  @Id
  private Long roleId;

  @Id
  private Long inheritedRoleId;

  /**
   * 复合主键。
   *
   * @param roleId 子角色 ID
   * @param inheritedRoleId 父角色 ID
   */
  public record Key(Long roleId, Long inheritedRoleId) implements Serializable {}
}
