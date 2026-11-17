package com.rbac.rbac1ddd.infrastructure.persistence.dao;

import com.rbac.rbac1ddd.infrastructure.persistence.po.UserRolePo;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** sys_user_role 表的 Spring Data 访问接口。 */
public interface UserRoleDao extends JpaRepository<UserRolePo, UserRolePo.Key> {

  /** 查询指定用户的全部角色分配。 */
  List<UserRolePo> findByUserId(long userId);

  /** 批量查询多个用户的角色分配。 */
  List<UserRolePo> findByUserIdIn(Collection<Long> userIds);

  /** 统计直接持有指定角色的用户数。 */
  long countByRoleId(long roleId);

  /** 查询直接持有任一指定角色的用户 ID，结果去重。 */
  @Query("select distinct link.userId from UserRolePo link where link.roleId in :roleIds")
  List<Long> findUserIdsByRoleIdIn(Collection<Long> roleIds);
}
