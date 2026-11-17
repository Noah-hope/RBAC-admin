package com.rbac.rbac1ddd.infrastructure.persistence.po;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.rbac.rbac1ddd.domain.api.Api;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 持久化对象：sys_api 表的一行，登记某个应用的一条接口路由。
 *
 * <p>{@code @CopyFrom(Api.class)} 让 CloneFactory 生成 copy(ApiPo, Api)，拷贝全部同名字段。
 * 唯一约束名与 V1__init_rbac1.sql 一致，仓储据此翻译重复错误。
 */
@Entity
@Table(
    name = "sys_api",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uq_api_operation",
          columnNames = {"application_id", "operation_id"}),
      @UniqueConstraint(
          name = "uq_api_route",
          columnNames = {"application_id", "http_method", "path"})
    })
@CopyFrom(Api.class)
@Getter
@Setter
@NoArgsConstructor
public class ApiPo {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long applicationId;

  /** OpenAPI 契约中的 operationId，用于在日志和审计中标识接口。 */
  @Column(nullable = false, length = 128)
  private String operationId;

  @Column(nullable = false, length = 10)
  private String httpMethod;

  /** 接口路径模板，与请求路由按原样匹配。 */
  @Column(nullable = false, length = 255)
  private String path;
}
