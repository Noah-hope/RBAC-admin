package com.rbac.rbac1ddd.infrastructure.persistence.dao;

import com.rbac.rbac1ddd.infrastructure.persistence.po.RoleInheritancePo;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** sys_role_inheritance 表的 Spring Data 访问接口。 */
public interface RoleInheritanceDao
    extends JpaRepository<RoleInheritancePo, RoleInheritancePo.Key> {

  /** 查询指定角色直接继承的全部父角色关联。 */
  List<RoleInheritancePo> findByRoleId(long roleId);

  /** 批量查询多个角色直接继承的父角色关联。 */
  List<RoleInheritancePo> findByRoleIdIn(Collection<Long> roleIds);
}
