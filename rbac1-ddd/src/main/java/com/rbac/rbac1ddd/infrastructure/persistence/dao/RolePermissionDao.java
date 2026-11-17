package com.rbac.rbac1ddd.infrastructure.persistence.dao;

import com.rbac.rbac1ddd.infrastructure.persistence.po.RolePermissionPo;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** sys_role_permission 表的 Spring Data 访问接口。 */
public interface RolePermissionDao extends JpaRepository<RolePermissionPo, RolePermissionPo.Key> {

  /** 查询指定角色直接拥有的全部权限关联。 */
  List<RolePermissionPo> findByRoleId(long roleId);

  /** 批量查询多个角色直接拥有的权限关联。 */
  List<RolePermissionPo> findByRoleIdIn(Collection<Long> roleIds);
}
