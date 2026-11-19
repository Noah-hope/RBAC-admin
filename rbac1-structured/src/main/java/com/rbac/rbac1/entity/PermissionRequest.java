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

/** sys_permission_request 表的一行：用户对单个权限的申请及审核结果。 */
@Entity
@Table(name = "sys_permission_request")
@Getter
@Setter
@NoArgsConstructor
public class PermissionRequest {

  /** 状态：待审核。 */
  public static final String PENDING = "PENDING";

  /** 状态：已通过，并为申请人分配了所选角色。 */
  public static final String APPROVED = "APPROVED";

  /** 状态：已拒绝。 */
  public static final String REJECTED = "REJECTED";

  /** 状态：申请人已通过其他途径获得该权限，申请自动结束。 */
  public static final String SATISFIED = "SATISFIED";

  /** 状态：申请人已撤回。 */
  public static final String WITHDRAWN = "WITHDRAWN";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long applicantUserId;

  @Column(nullable = false)
  private Long permissionId;

  @Column(nullable = false, length = 500)
  private String reason;

  @Column(nullable = false, length = 12)
  private String status;

  @Column()
  private Long reviewerUserId;

  @Column()
  private Long approvedRoleId;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column()
  private LocalDateTime reviewedAt;
}
