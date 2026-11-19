package com.rbac.rbac1.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** sys_role_create_request_inheritance 表的一行：申请中选择的父角色，同时保存提交时的角色名称快照。 */
@Entity
@Table(name = "sys_role_create_request_inheritance")
@IdClass(RoleCreationRequestInheritance.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class RoleCreationRequestInheritance {

  @Id
  private Long requestId;

  @Id
  private Long inheritedRoleId;

  @Column(nullable = false, length = 24)
  private String inheritedRoleName;

  /**
   * 复合主键。
   *
   * @param requestId 角色创建申请 ID
   * @param inheritedRoleId 申请继承的父角色 ID
   */
  public record Key(Long requestId, Long inheritedRoleId) implements Serializable {}
}
