package com.rbac.rbac1.entity;

import jakarta.persistence.*;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "sys_api",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"application_id", "operation_id"}),
      @UniqueConstraint(columnNames = {"application_id", "http_method", "path"})
    })
public class Api {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false)
  public Long applicationId;

  @Column(nullable = false, length = 128)
  public String operationId;

  @Column(nullable = false, length = 10)
  public String httpMethod;

  @Column(nullable = false, length = 255)
  public String path;
}
