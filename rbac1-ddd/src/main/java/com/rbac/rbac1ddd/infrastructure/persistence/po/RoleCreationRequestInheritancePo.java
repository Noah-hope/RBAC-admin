package com.rbac.rbac1ddd.infrastructure.persistence.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 持久化对象：sys_role_create_request_inheritance 表的一行，表示申请时所选父角色的 ID 和名称快照。
 *
 * <p>表中不设指向角色表的外键，父角色删除后快照仍然保留。
 */
@Entity
@Table(name = "sys_role_create_request_inheritance")
@IdClass(RoleCreationRequestInheritancePo.Key.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RoleCreationRequestInheritancePo {
  /** 申请 ID。 */
  @Id
  private Long requestId;

  /** 父角色 ID。 */
  @Id
  private Long inheritedRoleId;

  /** 提交申请时的父角色名称。 */
  @Column(nullable = false, length = 24)
  private String inheritedRoleName;

  /** 复合主键：申请 ID 与父角色 ID。 */
  public record Key(Long requestId, Long inheritedRoleId) implements Serializable {}
}
