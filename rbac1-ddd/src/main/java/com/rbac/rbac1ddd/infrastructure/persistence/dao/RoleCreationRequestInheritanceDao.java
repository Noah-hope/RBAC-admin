package com.rbac.rbac1ddd.infrastructure.persistence.dao;

import com.rbac.rbac1ddd.infrastructure.persistence.po.RoleCreationRequestInheritancePo;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** sys_role_create_request_inheritance 表的 Spring Data 访问接口。 */
public interface RoleCreationRequestInheritanceDao
    extends JpaRepository<RoleCreationRequestInheritancePo, RoleCreationRequestInheritancePo.Key> {

  /** 批量查询多个申请的父角色快照。 */
  List<RoleCreationRequestInheritancePo> findByRequestIdIn(Collection<Long> requestIds);
}
