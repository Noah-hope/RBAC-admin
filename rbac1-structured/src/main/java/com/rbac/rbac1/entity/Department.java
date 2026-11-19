package com.rbac.rbac1.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** sys_department 表的一行：部门树中的一个节点，顶层部门的上级为 null。 */
@Entity
@Table(name = "sys_department")
@Getter
@Setter
@NoArgsConstructor
public class Department {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long parentId;

  @Column(nullable = false, length = 24)
  private String name;
}
