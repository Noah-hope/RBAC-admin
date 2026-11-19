package com.rbac.rbac1.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** sys_api_permission 表的一行：权限与接口的关联。 */
@Entity
@Table(name = "sys_api_permission")
@IdClass(ApiPermission.Key.class)
@Getter
@Setter
@NoArgsConstructor
public class ApiPermission {

  @Id
  private Long permissionId;

  @Id
  private Long apiId;

  /**
   * 复合主键。
   *
   * @param permissionId 权限 ID
   * @param apiId 接口 ID
   */
  public record Key(Long permissionId, Long apiId) implements Serializable {}
}
