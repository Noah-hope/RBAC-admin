package com.rbac.rbac1.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** sys_role_create_request 表的一行：部门管理员提交的角色创建申请及审核结果。 */
@Entity
@Table(name = "sys_role_create_request")
@Getter
@Setter
@NoArgsConstructor
public class RoleCreationRequest {

  /** 状态：待审核。 */
  public static final String PENDING = "PENDING";

  /** 状态：已通过并创建了角色。 */
  public static final String APPROVED = "APPROVED";

  /** 状态：已拒绝。 */
  public static final String REJECTED = "REJECTED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long applicantUserId;

  @Column(nullable = false, length = 24)
  private String roleName;

  @Column(nullable = false, length = 48)
  private String roleDescription;

  @Column(nullable = false, length = 8)
  private String status;

  @Column()
  private Long reviewerUserId;

  @Column()
  private Long createdRoleId;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column()
  private LocalDateTime reviewedAt;
}
