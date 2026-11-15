package com.rbac.rbac1.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_permission_request")
public class PermissionRequest {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long applicantUserId;

  @Column(nullable = false)
  public Long permissionId;

  @Column(nullable = false, length = 500)
  public String reason;

  @Column(nullable = false, length = 12)
  public String status;

  @Column() public Long reviewerUserId;

  @Column() public Long approvedRoleId;

  @Column(nullable = false)
  public LocalDateTime createdAt;

  @Column() public LocalDateTime reviewedAt;
}
