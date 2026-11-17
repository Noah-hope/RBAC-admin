package com.rbac.rbac1ddd.infrastructure.persistence.po;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.rbac.rbac1ddd.domain.department.Department;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 持久化对象：sys_department 表的一行，按父部门 ID 组成部门树。
 *
 * <p>{@code @CopyFrom(Department.class)} 让 CloneFactory 生成 copy(DepartmentPo, Department)，
 * 拷贝 ID、名称和上级部门 ID。
 */
@Entity
@Table(name = "sys_department")
@CopyFrom(Department.class)
@Getter
@Setter
@NoArgsConstructor
public class DepartmentPo {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 父部门 ID，根部门为 null。 */
  private Long parentId;

  @Column(nullable = false, length = 24)
  private String name;
}
