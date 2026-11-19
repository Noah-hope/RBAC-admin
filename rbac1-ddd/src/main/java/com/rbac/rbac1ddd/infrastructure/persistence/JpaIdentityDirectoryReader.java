package com.rbac.rbac1ddd.infrastructure.persistence;

import com.rbac.rbac1ddd.application.auth.IdentityDirectoryReader;
import com.rbac.rbac1ddd.application.shared.PageResult;
import com.rbac.rbac1ddd.domain.user.UserStatus;
import com.rbac.rbac1ddd.infrastructure.persistence.dao.DepartmentDao;
import com.rbac.rbac1ddd.infrastructure.persistence.dao.UserDao;
import com.rbac.rbac1ddd.infrastructure.persistence.po.DepartmentPo;
import com.rbac.rbac1ddd.infrastructure.persistence.po.UserPo;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/** 统一身份目录的 JPA 查询适配器。目录读取不需要装载用户的角色集合。 */
@Repository
@RequiredArgsConstructor
public class JpaIdentityDirectoryReader implements IdentityDirectoryReader {

  private final DepartmentDao departments;
  private final UserDao users;

  /** 在数据库中分页读取本层部门，再批量解析当前页能展开的节点。 */
  @Override
  public PageResult<DepartmentResult> departments(
      Long parentId, String keyword, int page, int pageSize) {
    Page<DepartmentPo> result =
        departments.findDirectoryLevel(parentId, keyword, PageRequest.of(page - 1, pageSize));
    List<Long> ids = result.getContent().stream().map(DepartmentPo::getId).toList();
    Set<Long> parents =
        ids.isEmpty() ? Set.of() : Set.copyOf(departments.findDirectoryParentIds(ids));
    List<DepartmentResult> items = result.getContent().stream()
        .map(department -> new DepartmentResult(department.getId(), department.getName(),
            department.getParentId(), parents.contains(department.getId())))
        .toList();
    return new PageResult<>(items, page, pageSize, result.getTotalElements());
  }

  /** 按指定部门分页读取启用账号。 */
  @Override
  public PageResult<DepartmentUserResult> departmentUsers(
      long departmentId, String departmentName, String keyword, int page, int pageSize) {
    Page<UserPo> result = users.findDirectoryUsers(
        departmentId, UserStatus.ACTIVE.name(), keyword, PageRequest.of(page - 1, pageSize));
    List<DepartmentUserResult> items = result.getContent().stream()
        .map(user -> new DepartmentUserResult(
            user.getId(), user.getUsername(), user.getName(), departmentId, departmentName))
        .toList();
    return new PageResult<>(items, page, pageSize, result.getTotalElements());
  }

  /** 只读取组织内的启用账号，对外结果仅包含选人所需字段。 */
  @Override
  public Optional<UserResult> user(long userId) {
    return users.findById(userId)
        .filter(user -> user.getDepartmentId() != null && UserStatus.ACTIVE.name().equals(user.getStatus()))
        .map(user -> new UserResult(user.getId(), user.getUsername(), user.getName()));
  }
}
