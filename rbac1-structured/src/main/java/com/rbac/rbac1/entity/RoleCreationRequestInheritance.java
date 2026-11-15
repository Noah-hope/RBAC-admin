package com.rbac.rbac1.entity;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "sys_role_create_request_inheritance")
@IdClass(RoleCreationRequestInheritance.Key.class)
public class RoleCreationRequestInheritance {
  @Id public Long requestId;
  @Id public Long inheritedRoleId;

  @Column(nullable = false, length = 24) public String inheritedRoleName;

  public record Key(Long requestId, Long inheritedRoleId) implements Serializable {}
}
