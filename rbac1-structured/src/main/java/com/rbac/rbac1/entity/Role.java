package com.rbac.rbac1.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** sys_role 表的一行：业务角色或预置管理角色，名称唯一。 */
@Entity
@Table(
    name = "sys_role",
    uniqueConstraints = {
      @UniqueConstraint(name = "uq_role_name", columnNames = {"name"})
    })
@Getter
@Setter
@NoArgsConstructor
public class Role {

  /** 角色类型：业务角色，可配置权限和继承关系。 */
  public static final String BUSINESS = "BUSINESS";

  /** 角色类型：预置的总管理员角色。 */
  public static final String TOTAL_ADMIN = "TOTAL_ADMIN";

  /** 角色类型：预置的部门管理员角色。 */
  public static final String DEPARTMENT_ADMIN = "DEPARTMENT_ADMIN";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 24)
  private String name;

  @Column(nullable = false, length = 48)
  private String description;

  @Column(nullable = false, length = 16)
  private String roleType;
}
