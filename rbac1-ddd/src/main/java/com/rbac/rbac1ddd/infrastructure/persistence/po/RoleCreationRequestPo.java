package com.rbac.rbac1ddd.infrastructure.persistence.po;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.rbac.rbac1ddd.domain.request.RoleCreationRequest;
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
 * 持久化对象：sys_role_create_request 表的一行，表示部门管理员提交的一条角色创建申请。
 *
 * <p>{@code @CopyFrom(RoleCreationRequest.class)} 让 CloneFactory 生成同名同类型字段的拷贝；
 * 申请状态在领域层是枚举、在表中是字符串，单独转换。直接权限和父角色快照分别保存在两张关联表中。
 */
@Entity
@Table(name = "sys_role_create_request")
@CopyFrom(RoleCreationRequest.class)
@Getter
@Setter
@NoArgsConstructor
public class RoleCreationRequestPo {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long applicantUserId;

  @Column(nullable = false, length = 24)
  private String roleName;

  @Column(nullable = false, length = 48)
  private String roleDescription;

  /** 申请状态：PENDING、APPROVED 或 REJECTED。 */
  @Column(nullable = false, length = 8)
  @CopyFrom.Exclude(RoleCreationRequest.class)
  private String status;

  /** 审核人用户 ID，尚未审核时为 null。 */
  private Long reviewerUserId;

  /** 审批通过后实际创建的角色 ID。 */
  private Long createdRoleId;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  /** 审核时间（UTC），尚未审核时为 null。 */
  private LocalDateTime reviewedAt;
}
