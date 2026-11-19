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

/** sys_application 表的一行：接入权限中心的业务应用，名称和应用标识各自唯一。 */
@Entity
@Table(
    name = "sys_application",
    uniqueConstraints = {
      @UniqueConstraint(name = "uq_application_code", columnNames = {"code"}),
      @UniqueConstraint(name = "uq_application_name", columnNames = {"name"})
    })
@Getter
@Setter
@NoArgsConstructor
public class BusinessApplication {

  /** 权限中心自身的应用标识，该应用的权限自动授予总管理员。 */
  public static final String PERMISSION_CENTER = "permission-center";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 24)
  private String name;

  @Column(nullable = false, length = 64)
  private String code;
}
