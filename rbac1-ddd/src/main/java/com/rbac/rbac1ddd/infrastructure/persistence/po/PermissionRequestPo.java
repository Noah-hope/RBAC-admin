package com.rbac.rbac1ddd.infrastructure.persistence.po;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.rbac.rbac1ddd.domain.request.PermissionRequest;
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

/**
 * 持久化对象：sys_permission_request 表的一行，表示用户提交的一条权限申请。
 *
 * <p>{@code @CopyFrom(PermissionRequest.class)} 让 CloneFactory 生成 copy(PermissionRequestPo, PermissionRequest)；
 * 申请状态在领域层是枚举、在表中是字符串，单独转换。
 */
@Entity
@Table(name = "sys_permission_request")
@CopyFrom(PermissionRequest.class)
@Getter
@Setter
@NoArgsConstructor
public class PermissionRequestPo {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long applicantUserId;

  @Column(nullable = false)
  private Long permissionId;

  @Column(nullable = false, length = 500)
  private String reason;

  /** 申请状态，取值与领域层 RequestStatus 枚举名一致。 */
  @Column(nullable = false, length = 12)
  @CopyFrom.Exclude(PermissionRequest.class)
  private String status;

  /** 审核人用户 ID，尚未审核或自动结算为已满足时为 null。 */
  private Long reviewerUserId;

  /** 审批通过时分配的业务角色 ID。 */
  private Long approvedRoleId;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  /** 审核时间（UTC），尚未审核时为 null。 */
  private LocalDateTime reviewedAt;
}
