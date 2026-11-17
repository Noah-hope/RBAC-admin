package com.rbac.rbac1ddd.infrastructure.persistence.dao;

import com.rbac.rbac1ddd.infrastructure.persistence.po.UserPo;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** sys_user 表的 Spring Data 访问接口。 */
public interface UserDao extends JpaRepository<UserPo, Long> {

  /** 按登录账号查询用户。 */
  Optional<UserPo> findByUsername(String username);

  /** 按 ID 查询属于某个部门的用户并加悲观写锁，按 ID 升序加锁以避免死锁。 */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select account from UserPo account
      where account.id in :ids and account.departmentId is not null order by account.id
      """)
  List<UserPo> findOrganizationUsersForUpdate(Collection<Long> ids);

  /**
   * 在指定部门内按关键字分页查询用户，按 ID 升序；查询条件与 rbac1-structured 的 UserMapper.search 相同。
   *
   * <p>关键字为空字符串时不筛选；否则匹配姓名、登录账号，includeDepartmentName 为 true 时还匹配部门名称。
   */
  @Query(
      """
      select account from UserPo account, DepartmentPo department
      where department.id = account.departmentId and account.departmentId in :departmentIds
        and (:keyword = '' or locate(:keyword, account.name) > 0 or locate(:keyword, account.username) > 0
             or (:includeDepartmentName = true and locate(:keyword, department.name) > 0))
      order by account.id
      """)
  Page<UserPo> search(
      Collection<Long> departmentIds, String keyword, boolean includeDepartmentName, Pageable pageable);
}
