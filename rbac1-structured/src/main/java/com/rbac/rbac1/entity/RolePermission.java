package com.rbac.rbac1.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** sys_role_permission 表的一行：角色直接配置的权限。 */
@Entity
@Table(name = "sys_role_permission")
@IdClass(RolePermission.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class RolePermission {

  @Id
  private Long roleId;

  @Id
  private Long permissionId;

  /**
   * 复合主键。
   *
   * @param roleId 角色 ID
   * @param permissionId 权限 ID
   */
  public record Key(Long roleId, Long permissionId) implements Serializable {}
}
