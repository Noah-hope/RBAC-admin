package com.rbac.rbac0.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_role_create_request")
public class RoleCreationRequest {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long applicantUserId;

  @Column(nullable = false, length = 24)
  public String roleName;

  @Column(nullable = false, length = 48)
  public String roleDescription;

  @Column(nullable = false, length = 8)
  public String status;

  @Column() public Long reviewerUserId;

  @Column() public Long createdRoleId;

  @Column(nullable = false)
  public LocalDateTime createdAt;

  @Column() public LocalDateTime reviewedAt;
}
