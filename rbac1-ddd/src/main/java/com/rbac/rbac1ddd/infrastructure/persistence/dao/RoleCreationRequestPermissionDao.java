package com.rbac.rbac1ddd.infrastructure.persistence.dao;

import com.rbac.rbac1ddd.infrastructure.persistence.po.RoleCreationRequestPermissionPo;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** sys_role_create_request_permission 表的 Spring Data 访问接口。 */
public interface RoleCreationRequestPermissionDao
    extends JpaRepository<RoleCreationRequestPermissionPo, RoleCreationRequestPermissionPo.Key> {

  /** 批量查询多个申请的直接权限。 */
  List<RoleCreationRequestPermissionPo> findByRequestIdIn(Collection<Long> requestIds);
}
