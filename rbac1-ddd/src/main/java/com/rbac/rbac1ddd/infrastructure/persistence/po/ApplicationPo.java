package com.rbac.rbac1ddd.infrastructure.persistence.po;

import cn.edu.xmu.clonefactory.CopyFrom;
import com.rbac.rbac1ddd.domain.app.BusinessApplication;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 持久化对象：sys_application 表的一行，表示接入权限中心的一个应用。
 *
 * <p>{@code @CopyFrom(BusinessApplication.class)} 让 CloneFactory 生成 copy(ApplicationPo, BusinessApplication)，
 * 拷贝 ID、编码和名称。唯一约束名与 V1__init_rbac1.sql 一致，仓储据此翻译重复错误。
 */
@Entity
@Table(
    name = "sys_application",
    uniqueConstraints = {
      @UniqueConstraint(name = "uq_application_code", columnNames = "code"),
      @UniqueConstraint(name = "uq_application_name", columnNames = "name")
    })
@CopyFrom(BusinessApplication.class)
@Getter
@Setter
@NoArgsConstructor
public class ApplicationPo {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 24)
  private String name;

  /** 应用编码，接口路由查询时用它定位应用。 */
  @Column(nullable = false, length = 64)
  private String code;
}
