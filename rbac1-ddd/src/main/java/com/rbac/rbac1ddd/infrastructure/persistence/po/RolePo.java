package com.rbac.rbac1ddd.infrastructure.persistence.po;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.rbac.rbac1ddd.domain.role.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 持久化对象：sys_role 表的一行。只在基础设施层使用，不暴露给领域层。
 *
 * <p>{@code @CopyFrom(Role.class)} 让 CloneFactory 在编译期生成 copy(RolePo, Role)，把 ID、名称和说明拷贝过来。
 */
@Entity
@Table(name = "sys_role", uniqueConstraints = @UniqueConstraint(name = "uq_role_name", columnNames = "name"))
@CopyFrom(Role.class)
@Getter
@Setter
@NoArgsConstructor
public class RolePo {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 24)
  private String name;

  @Column(nullable = false, length = 48)
  private String description;

  /** 角色类型，取值与领域层 RoleType 枚举名一致。 */
  @Column(nullable = false, length = 16)
  private String roleType;
}
