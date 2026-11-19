package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.Role;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** sys_role 表的数据访问接口：角色检索、加锁读取，以及按用户读取管理身份和已分配角色。 */
public interface RoleMapper extends JpaRepository<Role, Long> {

  /**
   * 按角色类型查找唯一的预置管理角色（总管理员或部门管理员）。
   *
   * @param roleType 角色类型
   * @return 预置角色；业务角色有多个，不应按此方法查询
   */
  Role findByRoleType(String roleType);

  /**
   * 按 ID 查找指定类型的角色，类型不符时视为不存在。
   *
   * @param id 角色 ID
   * @param roleType 角色类型
   * @return 匹配的角色
   */
  Optional<Role> findByIdAndRoleType(long id, String roleType);

  /**
   * 加写锁读取角色，修改或删除角色时防止并发修改同一角色。
   *
   * @param id 角色 ID
   * @return 加锁后的角色
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select role from Role role where role.id = :id")
  Optional<Role> findRoleForUpdate(long id);

  /**
   * 分页检索角色：角色类型为 null 时不限类型，关键字为空串时不过滤，否则匹配角色名称子串。按 ID 升序。
   *
   * @param keyword 关键字，空串表示不过滤
   * @param roleType 角色类型，null 表示不限
   * @param pageable 分页参数
   * @return 当前页的角色
   */
  @Query(
      """
select role from Role role
where (:roleType is null or role.roleType = :roleType) and (:keyword = '' or locate(:keyword, role.name) > 0)
order by role.id
""")
  Page<Role> searchRoles(String keyword, String roleType, Pageable pageable);

  /**
   * 读取用户被分配的预置管理角色类型，用于确定会话中的管理身份。
   *
   * @param userId 用户 ID
   * @return 管理角色类型：TOTAL_ADMIN、DEPARTMENT_ADMIN，没有时为空列表
   */
  @Query(
      """
      select role.roleType from Role role, UserRole assignment
      where assignment.userId = :userId and assignment.roleId = role.id
        and role.roleType in ('TOTAL_ADMIN', 'DEPARTMENT_ADMIN')
      """)
  List<String> findManagementRoles(long userId);

  /**
   * 一次读取多个用户被分配的全部角色（含管理角色），用于用户列表和详情展示，避免逐个用户查询。
   *
   * @param userIds 用户 ID
   * @return 用户与角色的对应行，按角色 ID 升序
   */
  @Query(
      """
select assignment.userId as userId, role.id as id, role.name as name, role.roleType as roleType
from Role role, UserRole assignment
where assignment.userId in :userIds and assignment.roleId = role.id
order by role.id
""")
  List<UserRoleRow> findAssignedRoles(List<Long> userIds);

  /** {@link #findAssignedRoles} 的投影：一个用户被分配的一个角色。 */
  interface UserRoleRow {

    /**
     * 被分配角色的用户。
     *
     * @return 用户 ID
     */
    long getUserId();

    /**
     * 分配给该用户的角色。
     *
     * @return 角色 ID
     */
    long getId();

    /**
     * 角色名称，用于直接展示。
     *
     * @return 角色名称
     */
    String getName();

    /**
     * 角色类型，用于区分业务角色和管理角色。
     *
     * @return 角色类型
     */
    String getRoleType();
  }
}
