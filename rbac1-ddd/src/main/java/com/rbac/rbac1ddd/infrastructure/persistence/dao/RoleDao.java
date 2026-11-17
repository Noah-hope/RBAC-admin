package com.rbac.rbac1ddd.infrastructure.persistence.dao;

import com.rbac.rbac1ddd.infrastructure.persistence.po.RolePo;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** sys_role 表的 Spring Data 访问接口。 */
public interface RoleDao extends JpaRepository<RolePo, Long> {

  /** 按 ID 查询角色并加悲观写锁，用于需要串行化的角色修改。 */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select role from RolePo role where role.id = :id")
  Optional<RolePo> findForUpdate(long id);

  /** 按角色类型查询 ID 最小的角色，用于查找预置管理员角色。 */
  Optional<RolePo> findFirstByRoleTypeOrderById(String roleType);

  /** 按角色类型和名称关键字分页查询，按 ID 升序；类型为 null 或关键字为空串时不按该条件过滤。 */
  @Query(
      """
      select role from RolePo role
      where (:roleType is null or role.roleType = :roleType)
        and (:keyword = '' or locate(:keyword, role.name) > 0)
      order by role.id
      """)
  Page<RolePo> search(String keyword, String roleType, Pageable pageable);
}
