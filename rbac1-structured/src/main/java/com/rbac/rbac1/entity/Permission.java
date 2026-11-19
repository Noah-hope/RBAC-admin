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

/** sys_permission 表的一行：应用内的菜单或按钮权限，按钮的上级是同一应用的菜单。 */
@Entity
@Table(
    name = "sys_permission",
    uniqueConstraints = {
      @UniqueConstraint(name = "uq_permission_application_code", columnNames = {"application_id", "code"})
    })
@Getter
@Setter
@NoArgsConstructor
public class Permission {

  /** 权限类型：菜单。 */
  public static final String MENU = "MENU";

  /** 权限类型：按钮。 */
  public static final String BUTTON = "BUTTON";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long applicationId;

  private Long parentId;

  @Column(nullable = false, length = 24)
  private String name;

  @Column(nullable = false, length = 64)
  private String code;

  @Column(nullable = false, length = 6)
  private String type;
}
