package com.rbac.rbac1ddd.infrastructure.persistence.dao;

import com.rbac.rbac1ddd.infrastructure.persistence.po.ApplicationPo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** sys_application 表的 Spring Data 访问接口。 */
public interface ApplicationDao extends JpaRepository<ApplicationPo, Long> {

  /** 按应用编码查询应用。 */
  Optional<ApplicationPo> findByCode(String code);
}
