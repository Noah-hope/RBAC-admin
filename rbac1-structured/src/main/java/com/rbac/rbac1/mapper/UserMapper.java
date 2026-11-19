package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.UserAccount;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** sys_user 表的数据访问接口：登录查找、组织内用户的加锁读取和按管理范围分页检索。 */
public interface UserMapper extends JpaRepository<UserAccount, Long> {

  /**
   * 按唯一的登录账号查找用户，用于登录。
   *
   * @param username 登录账号
   * @return 匹配的用户
   */
  Optional<UserAccount> findByUsername(String username);

  /**
   * 加写锁读取组织内（有所属部门）的用户，按 ID 升序加锁以避免并发修改时死锁；
   * 没有部门的预置总管理员不会返回，调用方据此按“用户不存在”处理。
   *
   * @param ids 用户 ID
   * @return 存在且属于某个部门的用户
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
          select account from UserAccount account
          where account.id in :ids and account.departmentId is not null order by account.id
          """)
  List<UserAccount> findOrganizationUsersForUpdate(List<Long> ids);

  /**
   * 分页检索指定部门内的用户，关键字匹配姓名或登录账号；勾选时还匹配所属部门名称。按 ID 升序。
   *
   * @param departmentIds 查询范围内的部门 ID
   * @param keyword 关键字，空串表示不过滤
   * @param includeDepartmentName 关键字是否同时匹配部门名称
   * @param pageable 分页参数
   * @return 当前页的用户
   */
  @Query("""
          select account from UserAccount account, Department department
          where department.id = account.departmentId and account.departmentId in :departmentIds
            and (:keyword = '' or locate(:keyword, account.name) > 0 or locate(:keyword, account.username) > 0
                 or (:includeDepartmentName = true and locate(:keyword, department.name) > 0))
          order by account.id
          """)
  Page<UserAccount> search(
      Collection<Long> departmentIds,
      String keyword,
      boolean includeDepartmentName,
      Pageable pageable);

  /** 分页查找当前部门下的启用账号，按姓名或登录账号匹配。 */
  @Query(
      """
      select account from UserAccount account
      where account.departmentId = :departmentId and account.status = :activeStatus
        and (:keyword = '' or locate(:keyword, account.name) > 0 or locate(:keyword, account.username) > 0)
      order by account.id
      """)
  Page<UserAccount> findDirectoryUsers(
      long departmentId, String activeStatus, String keyword, Pageable pageable);
}
