package com.rbac.rbac1.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** sys_role_create_request_permission 表的一行：申请中选择的直接权限。 */
@Entity
@Table(name = "sys_role_create_request_permission")
@IdClass(RoleCreationRequestPermission.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class RoleCreationRequestPermission {

  @Id
  private Long requestId;

  @Id
  private Long permissionId;

  /**
   * 复合主键。
   *
   * @param requestId 角色创建申请 ID
   * @param permissionId 申请的直接权限 ID
   */
  public record Key(Long requestId, Long permissionId) implements Serializable {}
}
