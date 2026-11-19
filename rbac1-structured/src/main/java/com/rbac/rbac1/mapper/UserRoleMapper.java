package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.UserRole;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** sys_user_role 表的数据访问接口：维护用户与角色的分配关系。 */
public interface UserRoleMapper extends JpaRepository<UserRole, UserRole.Key> {

  /**
   * 读取一批用户的全部角色分配，用于计算这些用户的有效权限。
   *
   * @param userIds 用户 ID
   * @return 这些用户的角色分配
   */
  List<UserRole> findByUserIdIn(Collection<Long> userIds);

  /**
   * 读取分配了任一指定角色的记录；角色权限变化后据此找出受影响的用户。
   *
   * @param roleIds 角色 ID
   * @return 这些角色的分配记录
   */
  List<UserRole> findByRoleIdIn(Collection<Long> roleIds);

  /**
   * 统计每个角色直接分配的用户数，审核权限申请时展示可选角色的影响范围。
   *
   * @return 每个已分配角色的用户数，未分配的角色不出现
   */
  @Query(
      "select a.roleId as roleId, count(a.userId) as userCount from UserRole a group by a.roleId")
  List<RoleUserCount> countRoleUsers();

  /** {@link #countRoleUsers} 的投影：一个角色及其直接分配的用户数。 */
  interface RoleUserCount {

    /**
     * 被统计的角色。
     *
     * @return 角色 ID
     */
    long getRoleId();

    /**
     * 直接分配该角色的用户数。
     *
     * @return 用户数
     */
    long getUserCount();
  }

  /**
   * 判断角色是否已分配给任何用户，系统初始化时据此判断是否已有总管理员。
   *
   * @param roleId 角色 ID
   * @return 已分配时为 true
   */
  boolean existsByRoleId(Long roleId);

  /**
   * 统计直接分配该角色的用户数，用于角色详情和删除前检查。
   *
   * @param roleId 角色 ID
   * @return 用户数
   */
  long countByRoleId(long roleId);

  /**
   * 读取一批用户的业务角色分配（不含预置管理角色），按用户和角色 ID 排序，用于替换和批量变更业务角色。
   *
   * @param userIds 用户 ID
   * @return 这些用户的业务角色分配
   */
  @Query(
      """
      select link from UserRole link, Role role
      where link.userId in :userIds and link.roleId = role.id and role.roleType = 'BUSINESS'
      order by link.userId, link.roleId
      """)
  List<UserRole> findBusinessAssignments(List<Long> userIds);
}
