package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.RoleInheritance;
import org.springframework.data.jpa.repository.JpaRepository;

/** sys_role_inheritance 表的数据访问接口；继承图由 Service 整体读取后在内存中计算。 */
public interface RoleInheritanceMapper extends JpaRepository<RoleInheritance, RoleInheritance.Key> {

  /**
   * 判断角色是否作为子角色或父角色出现在继承关系中；两个参数传同一个角色 ID，用于删除前检查。
   *
   * @param roleId 作为子角色匹配的角色 ID
   * @param inheritedRoleId 作为父角色匹配的角色 ID
   * @return 存在任一继承关系时为 true
   */
  boolean existsByRoleIdOrInheritedRoleId(long roleId, long inheritedRoleId);
}
