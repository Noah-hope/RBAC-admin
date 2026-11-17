package com.rbac.rbac1ddd.infrastructure.persistence;

import static java.util.stream.Collectors.*;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.shared.DomainPage;
import com.rbac.rbac1ddd.domain.user.User;
import com.rbac.rbac1ddd.domain.user.UserRepository;
import com.rbac.rbac1ddd.domain.user.UserStatus;
import com.rbac.rbac1ddd.infrastructure.persistence.dao.UserDao;
import com.rbac.rbac1ddd.infrastructure.persistence.dao.UserRoleDao;
import com.rbac.rbac1ddd.infrastructure.persistence.po.UserPo;
import com.rbac.rbac1ddd.infrastructure.persistence.po.UserRolePo;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/** 用户仓储的 JPA 实现：一个用户聚合对应 sys_user 和 sys_user_role 两张表。 */
@Repository
@RequiredArgsConstructor
class JpaUserRepository implements UserRepository {

  /** 登录账号唯一约束名，与 V1__init_rbac1.sql 中的 uq_user_username 一致。 */
  private static final String USERNAME_CONSTRAINT = "uq_user_username";
  private final UserDao users;
  private final UserRoleDao assignments;

  /** 按 ID 加载用户聚合，包括直接分配的角色。 */
  @Override
  public Optional<User> findById(long id) {
    return users.findById(id).map(po -> toDomain(List.of(po)).getFirst());
  }

  /** 按登录账号加载用户聚合，包括直接分配的角色。 */
  @Override
  public Optional<User> findByUsername(String username) {
    return users.findByUsername(username).map(po -> toDomain(List.of(po)).getFirst());
  }

  /** 按 ID 批量加载用户聚合，不存在的 ID 被忽略。 */
  @Override
  public List<User> findAllById(Collection<Long> ids) {
    return toDomain(users.findAllById(ids));
  }

  /** 按 ID 加悲观写锁加载属于某个部门的用户聚合。 */
  @Override
  public List<User> findOrganizationUsersForUpdate(Collection<Long> ids) {
    return toDomain(users.findOrganizationUsersForUpdate(ids));
  }

  /** 按部门范围和关键字分页查询，再批量加载这一页用户的角色。 */
  @Override
  public DomainPage<User> search(
      Collection<Long> departmentIds,
      String keyword,
      boolean includeDepartmentName,
      int page,
      int pageSize) {
    Page<UserPo> result =
        users.search(
            departmentIds, keyword, includeDepartmentName, PageRequest.of(page - 1, pageSize));
    return new DomainPage<>(toDomain(result.getContent()), result.getTotalElements());
  }

  /** 统计直接持有指定角色的用户数。 */
  @Override
  public long countHolding(long roleId) {
    return assignments.countByRoleId(roleId);
  }

  /** 查询直接持有任一指定角色的用户 ID；角色集合为空时直接返回空列表。 */
  @Override
  public List<Long> findIdsHoldingAny(Collection<Long> roleIds) {
    return roleIds.isEmpty() ? List.of() : assignments.findUserIdsByRoleIdIn(roleIds);
  }

  /** 保存用户聚合：新用户先插入账号并回填 ID，已有用户更新账号资料，再只增删发生变化的角色关联记录。 */
  @Override
  public void save(User user) {
    if (user.getId() == null) {
      insertAccount(user);
    } else {
      updateAccount(user);
    }
    Set<Long> wanted = user.getRoleIds();
    List<UserRolePo> current = assignments.findByUserId(user.getId());
    Set<Long> currentIds = current.stream().map(UserRolePo::getRoleId).collect(toSet());
    assignments.deleteAll(current.stream().filter(a -> !wanted.contains(a.getRoleId())).toList());
    assignments.saveAll(
        wanted.stream()
            .filter(id -> !currentIds.contains(id))
            .map(id -> new UserRolePo(user.getId(), id))
            .toList());
  }

  /**
   * 插入新账号行并回填 ID，把登录账号唯一约束冲突就地转换为业务错误。
   *
   * <p>用 saveAndFlush 在当前方法内触发约束检查。
   */
  private void insertAccount(User user) {
    UserPo po = CloneFactory.copy(new UserPo(), user);
    po.setStatus(user.getStatus().name());
    saveCheckingUsername(po);
    user.assignId(po.getId());
  }

  /**
   * 把用户资料拷贝到已有账号行。
   *
   * <p>登录账号改变时用 saveAndFlush 就地检查唯一约束；其他字段随事务提交，不额外 flush。
   */
  private void updateAccount(User user) {
    UserPo po = users.findById(user.getId()).orElseThrow();
    boolean usernameChanged = !po.getUsername().equals(user.getUsername());
    CloneFactory.copy(po, user);
    po.setStatus(user.getStatus().name());
    if (usernameChanged) {
      saveCheckingUsername(po);
    } else {
      users.save(po);
    }
  }

  /** 保存账号行并立即触发约束检查，登录账号重复时转换为 DUPLICATE，其他约束错误原样抛出。 */
  private void saveCheckingUsername(UserPo po) {
    try {
      users.saveAndFlush(po);
    } catch (DataIntegrityViolationException error) {
      String message = String.valueOf(error.getMostSpecificCause().getMessage());
      if (message.toLowerCase(Locale.ROOT).contains(USERNAME_CONSTRAINT)) {
        throw new DomainException(DomainException.Reason.DUPLICATE, "该用户名已存在。");
      }
      throw error;
    }
  }

  /** 批量重建用户聚合：按用户 ID 一次查出角色分配。 */
  private List<User> toDomain(List<UserPo> pos) {
    if (pos.isEmpty()) {
      return List.of();
    }
    Map<Long, Set<Long>> roles =
        assignments.findByUserIdIn(pos.stream().map(UserPo::getId).toList()).stream()
            .collect(groupingBy(UserRolePo::getUserId, mapping(UserRolePo::getRoleId, toSet())));
    return pos.stream()
        .map(
            po ->
                new User(
                    po.getId(),
                    po.getUsername(),
                    po.getName(),
                    po.getDepartmentId(),
                    po.getPasswordHash(),
                    UserStatus.valueOf(po.getStatus()),
                    roles.getOrDefault(po.getId(), Set.of())))
        .toList();
  }
}
