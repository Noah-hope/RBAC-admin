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
    name = "sys_application",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"code"}),
      @UniqueConstraint(columnNames = {"name"})
    })
public class BusinessApplication {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, length = 24)
  public String name;

  @Column(nullable = false, length = 64)
  public String code;
}
