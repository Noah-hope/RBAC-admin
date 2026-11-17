package com.rbac.rbac1ddd.infrastructure.persistence.po;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 持久化对象：sys_role_create_request_permission 表的一行，表示申请中的一项直接权限。 */
@Entity
@Table(name = "sys_role_create_request_permission")
@IdClass(RoleCreationRequestPermissionPo.Key.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RoleCreationRequestPermissionPo {
  /** 申请 ID。 */
  @Id
  private Long requestId;

  /** 申请的直接权限 ID。 */
  @Id
  private Long permissionId;

  /** 复合主键：申请 ID 与权限 ID。 */
  public record Key(Long requestId, Long permissionId) implements Serializable {}
}
