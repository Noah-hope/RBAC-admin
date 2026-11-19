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

/** sys_api 表的一行：业务应用登记的一个接口，按应用内的操作标识和路由唯一。 */
@Entity
@Table(
    name = "sys_api",
    uniqueConstraints = {
      @UniqueConstraint(name = "uq_api_operation", columnNames = {"application_id", "operation_id"}),
      @UniqueConstraint(name = "uq_api_route", columnNames = {"application_id", "http_method", "path"})
    })
@Getter
@Setter
@NoArgsConstructor
public class Api {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long applicationId;

  @Column(nullable = false, length = 128)
  private String operationId;

  @Column(nullable = false, length = 10)
  private String httpMethod;

  @Column(nullable = false, length = 255)
  private String path;
}
