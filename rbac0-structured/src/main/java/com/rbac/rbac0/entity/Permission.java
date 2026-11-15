package com.rbac.rbac0.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "sys_permission",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"application_id", "code"})})
public class Permission {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long applicationId;

  public Long parentId;

  @Column(nullable = false, length = 24)
  public String name;

  @Column(nullable = false, length = 64)
  public String code;

  @Column(nullable = false, length = 6)
  public String type;
}
