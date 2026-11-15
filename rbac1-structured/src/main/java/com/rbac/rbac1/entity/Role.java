package com.rbac.rbac1.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "sys_role",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"name"})})
public class Role {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, length = 24)
  public String name;

  @Column(nullable = false, length = 48)
  public String description;

  @Column(nullable = false, length = 16)
  public String roleType;
}
