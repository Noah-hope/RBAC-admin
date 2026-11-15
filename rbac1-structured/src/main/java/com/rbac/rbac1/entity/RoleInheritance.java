package com.rbac.rbac1.entity;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "sys_role_inheritance")
@IdClass(RoleInheritance.Key.class)
public class RoleInheritance {
  @Id public Long roleId;
  @Id public Long inheritedRoleId;

  public record Key(Long roleId, Long inheritedRoleId) implements Serializable {}
}
