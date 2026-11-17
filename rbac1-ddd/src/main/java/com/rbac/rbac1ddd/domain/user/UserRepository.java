package com.rbac.rbac1ddd.domain.user;

import com.rbac.rbac1ddd.domain.shared.DomainPage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 用户仓储接口：以用户 + 用户角色关联为一个聚合存取。 */
public interface UserRepository {

  /**
   * 按 ID 读取用户聚合。
   *
   * @param id 用户 ID
   * @return 用户不存在时为空
   */
  Optional<User> findById(long id);

  /**
   * 按登录账号读取用户聚合，用于登录。
   *
   * @param username 登录账号
   * @return 账号不存在时为空
   */
  Optional<User> findByUsername(String username);

  /**
   * 按 ID 批量读取用户聚合，不存在的 ID 被忽略。
   *
   * @param ids 用户 ID
   * @return 找到的用户
   */
  List<User> findAllById(Collection<Long> ids);

  /**
   * 加行锁读取属于某个部门的用户，按 ID 排序。
   *
   * <p>固定加锁顺序，减少批量分配时并发事务互相死锁的可能。
   *
   * @param ids 用户 ID
   * @return 找到且属于某个部门的用户
   */
  List<User> findOrganizationUsersForUpdate(Collection<Long> ids);

  /**
   * 在指定部门内按关键字分页查询用户，按 ID 升序。
   *
   * <p>关键字匹配姓名或登录账号；includeDepartmentName 为 true 时也匹配所属部门名称。
   * 是否区分大小写由数据库排序规则决定，与 rbac1-structured 相同。
   *
   * @param departmentIds 部门范围，不能为空
   * @param keyword 关键字，空字符串表示不筛选
   * @param includeDepartmentName 是否同时匹配部门名称
   * @param page 页码，从 1 开始
   * @param pageSize 每页条数
   * @return 当前页用户和总数
   */
  DomainPage<User> search(
      Collection<Long> departmentIds,
      String keyword,
      boolean includeDepartmentName,
      int page,
      int pageSize);

  /**
   * 统计持有某个角色的用户数，用于删除角色前检查。
   *
   * @param roleId 角色 ID
   * @return 持有该角色的用户数
   */
  long countHolding(long roleId);

  /**
   * 查找持有任一指定角色的用户，用于角色变化后结算受影响用户的权限申请。
   *
   * @param roleIds 角色 ID
   * @return 用户 ID
   */
  List<Long> findIdsHoldingAny(Collection<Long> roleIds);

  /**
   * 保存用户聚合：新用户插入账号并回填 ID；已有用户更新账号资料，并只新增或解除发生变化的用户角色关联。
   *
   * @param user 用户聚合
   * @throws com.rbac.rbac1ddd.domain.shared.DomainException 登录账号已被其他用户使用时为 DUPLICATE
   */
  void save(User user);
}
