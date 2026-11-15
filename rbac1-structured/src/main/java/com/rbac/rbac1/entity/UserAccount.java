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
    name = "sys_user",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"username"})})
public class UserAccount {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public Long departmentId;

  @Column(nullable = false, length = 32)
  public String username;

  @Column(nullable = false, length = 24)
  public String name;

  @Column(nullable = false)
  public String passwordHash;

  @Column(nullable = false, length = 8)
  public String status;
}
