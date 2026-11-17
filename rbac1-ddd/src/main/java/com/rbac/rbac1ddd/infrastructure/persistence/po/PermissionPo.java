package com.rbac.rbac1ddd.infrastructure.persistence.po;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.rbac.rbac1ddd.domain.permission.PermissionDefinition;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 持久化对象：sys_permission 表的一行，权限按父权限 ID 组成目录树。
 *
 * <p>{@code @CopyFrom(PermissionDefinition.class)} 让 CloneFactory 生成 copy(PermissionPo, PermissionDefinition)，
 * 拷贝同名同类型字段；权限类型在领域层是枚举、在表中是字符串，单独转换。唯一约束名与 V1__init_rbac1.sql 一致。
 */
@Entity
@Table(
    name = "sys_permission",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_permission_application_code",
            columnNames = {"application_id", "code"}))
@CopyFrom(PermissionDefinition.class)
@Getter
@Setter
@NoArgsConstructor
public class PermissionPo {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long applicationId;

  /** 父权限 ID，顶层权限为 null。 */
  private Long parentId;

  @Column(nullable = false, length = 24)
  private String name;

  @Column(nullable = false, length = 64)
  private String code;

  /** 权限类型，取值与领域层 PermissionType 枚举名一致。 */
  @Column(nullable = false, length = 6)
  @CopyFrom.Exclude(PermissionDefinition.class)
  private String type;
}
