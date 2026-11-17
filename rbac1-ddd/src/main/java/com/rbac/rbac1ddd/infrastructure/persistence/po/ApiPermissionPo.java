package com.rbac.rbac1ddd.infrastructure.persistence.po;

import jakarta.persistence.*;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 持久化对象：sys_api_permission 表的一行，表示访问某个管理接口需要的权限。 */
@Entity
@Table(name = "sys_api_permission")
@IdClass(ApiPermissionPo.Key.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ApiPermissionPo {
  @Id
  private Long permissionId;

  @Id
  private Long apiId;

  /** 复合主键：权限 ID 与接口 ID。 */
  public record Key(Long permissionId, Long apiId) implements Serializable {}
}
