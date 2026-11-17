package com.rbac.rbac1ddd.infrastructure.persistence.po;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 持久化对象：sys_role_permission 表的一行，表示角色直接拥有的一项权限。 */
@Entity
@Table(name = "sys_role_permission")
@IdClass(RolePermissionPo.Key.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissionPo {
  @Id
  private Long roleId;

  @Id
  private Long permissionId;

  /** 复合主键：角色 ID 与权限 ID。 */
  public record Key(Long roleId, Long permissionId) implements Serializable {}
}
