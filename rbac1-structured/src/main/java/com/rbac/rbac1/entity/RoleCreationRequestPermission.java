package com.rbac.rbac1.entity;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "sys_role_create_request_permission")
@IdClass(RoleCreationRequestPermission.Key.class)
public class RoleCreationRequestPermission {
  @Id public Long requestId;

  @Id public Long permissionId;

  public record Key(Long requestId, Long permissionId) implements Serializable {}
}
