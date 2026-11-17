package com.rbac.rbac1ddd.application.user;

import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.INVALID_REQUEST;
import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.NOT_FOUND;

import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.application.auth.PasswordHasher;
import com.rbac.rbac1ddd.application.auth.SessionStore;
import com.rbac.rbac1ddd.application.role.RoleRefResult;
import com.rbac.rbac1ddd.application.shared.OperationRecorder;
import com.rbac.rbac1ddd.application.shared.OperationTarget;
import com.rbac.rbac1ddd.application.shared.PageResult;
import com.rbac.rbac1ddd.domain.department.Department;
import com.rbac.rbac1ddd.domain.department.DepartmentRepository;
import com.rbac.rbac1ddd.domain.department.DepartmentTree;
import com.rbac.rbac1ddd.domain.role.RoleHierarchy;
import com.rbac.rbac1ddd.domain.role.RoleRepository;
import com.rbac.rbac1ddd.domain.role.RoleType;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.shared.DomainPage;
import com.rbac.rbac1ddd.domain.user.User;
import com.rbac.rbac1ddd.domain.user.UserRepository;
import com.rbac.rbac1ddd.domain.user.UserStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用服务：用户查询、新增和修改（资料、账号状态、跨部门调动、任免部门管理员）。
 *
 * <p>只负责编排：权限守卫由 {@link Operator} 完成，资料变化由 {@link User} 的业务方法完成，
 * 这里负责读取和保存聚合、计算密码摘要、停用后撤销会话、写操作记录。
 * 操作记录的动作、明细、范围与 rbac1-structured 的 UserService 相同。
 */
@Service
@RequiredArgsConstructor
public class UserApplicationService {
  private final UserRepository users;
  private final DepartmentRepository departments;
  private final RoleRepository roles;
  private final PasswordHasher passwords;
  private final SessionStore sessions;
  private final OperationRecorder recorder;

  /**
   * 分页查询管理范围内的用户；选择部门时包括其全部下级部门。
   *
   * @param departmentId 所选部门，null 表示管理范围内的全部部门
   * @param keyword 关键字，空字符串表示不筛选
   * @param includeDepartmentName 关键字是否同时匹配部门名称
   */
  @Transactional(readOnly = true)
  public PageResult<UserResult> list(
      Operator operator,
      Long departmentId,
      String keyword,
      boolean includeDepartmentName,
      int page,
      int pageSize) {
    DepartmentTree tree = departments.loadTree();
    Set<Long> scope = operator.manageableDepartments(tree, departmentId);
    if (scope.isEmpty()) return new PageResult<>(List.of(), page, pageSize, 0);
    DomainPage<User> result = users.search(scope, keyword, includeDepartmentName, page, pageSize);
    RoleHierarchy hierarchy = roles.loadHierarchy();
    return new PageResult<>(
        result.items().stream().map(user -> view(user, tree, hierarchy)).toList(),
        page,
        pageSize,
        result.total());
  }

  /** 查询管理范围内的用户；没有所属部门的账号（如总管理员）视为不存在。 */
  @Transactional(readOnly = true)
  public UserResult get(Operator operator, long userId) {
    operator.requireAdministrator();
    User user =
        users
            .findById(userId)
            .filter(found -> found.getDepartmentId() != null)
            .orElseThrow(UserApplicationService::notFound);
    DepartmentTree tree = departments.loadTree();
    operator.requireManagedDepartment(tree, user.getDepartmentId());
    return view(user, tree, roles.loadHierarchy());
  }

  /**
   * 在管理范围内的部门新增用户，只保存初始密码的摘要；总管理员可同时任命为部门管理员。
   *
   * <p>写入「新增用户」记录（按部门可见）；任命时另写「任命部门管理员」全局记录。
   */
  @Transactional
  public UserResult create(Operator operator, UserCreateCommand command) {
    DepartmentTree tree = departments.loadTree();
    Department department = operator.requireManagedDepartment(tree, command.departmentId());
    if (command.departmentAdministrator() != null) operator.requireOrganizationAuthority();
    User user =
        User.create(
            command.username(),
            command.name(),
            department.getId(),
            passwords.hash(command.initialPassword()),
            command.status());
    users.save(user);
    recorder.recordUserScope(
        operator, "新增用户", "初始状态为" + user.getStatus(), List.of(OperationTarget.user(user)));
    if (Boolean.TRUE.equals(command.departmentAdministrator())) {
      changeDepartmentAdministrator(operator, user, true);
      users.save(user);
    }
    return view(user, tree, roles.loadHierarchy());
  }

  /**
   * 修改用户。部门管理员可以修改管理范围内用户的账号、姓名和状态；调动和任免部门管理员仅限总管理员。
   *
   * <p>按资料、状态、调动、任免的顺序处理，每类有实际变化才写一条记录；资料和状态记录按调动前的部门可见。
   * 停用账号时撤销该用户的全部会话，已签发的令牌立即失效。
   */
  @Transactional
  public UserResult update(Operator operator, long userId, UserUpdateCommand command) {
    operator.requireAdministrator();
    User user =
        users.findOrganizationUsersForUpdate(List.of(userId)).stream()
            .findFirst()
            .orElseThrow(UserApplicationService::notFound);
    DepartmentTree tree = departments.loadTree();
    Department original = operator.requireManagedDepartment(tree, user.getDepartmentId());
    if (command.changesOrganization()) operator.requireOrganizationAuthority();
    if (command.isEmpty()) throw new DomainException(INVALID_REQUEST, "请填写要修改的用户信息。");
    Department target =
        command.departmentId() == null || command.departmentId().equals(original.getId())
            ? original
            : operator.requireManagedDepartment(tree, command.departmentId());

    List<String> changes = new ArrayList<>();
    String oldUsername = user.getUsername();
    if (command.username() != null && user.changeUsername(command.username()))
      changes.add("用户名从「" + oldUsername + "」改为「" + command.username() + "」");
    String oldName = user.getName();
    if (command.name() != null && user.rename(command.name()))
      changes.add("姓名从「" + oldName + "」改为「" + command.name() + "」");
    if (!changes.isEmpty())
      recorder.recordUserScope(
          operator, "修改用户", String.join("，", changes), List.of(OperationTarget.user(user)));

    UserStatus oldStatus = user.getStatus();
    if (command.status() != null && user.changeStatus(command.status())) {
      if (!user.isActive()) sessions.revokeAllOf(userId);
      recorder.recordUserScope(
          operator,
          user.isActive() ? "启用账号" : "停用账号",
          "状态从 " + oldStatus + " 改为 " + user.getStatus(),
          List.of(OperationTarget.user(user)));
    }
    if (user.transferTo(target.getId()))
      recorder.recordGlobal(
          operator,
          "跨部门调动",
          "从「" + original.getName() + "」调至「" + target.getName() + "」",
          OperationTarget.globalUser(user));
    if (command.departmentAdministrator() != null)
      changeDepartmentAdministrator(operator, user, command.departmentAdministrator());
    users.save(user);
    return view(user, tree, roles.loadHierarchy());
  }

  /** 任命或撤销部门管理员，角色有实际变化才写全局记录（动作和明细相同）。 */
  private void changeDepartmentAdministrator(Operator operator, User user, boolean appointed) {
    long roleId = roles.findByType(RoleType.DEPARTMENT_ADMIN).orElseThrow().getId();
    if (!user.changeDepartmentAdministrator(appointed, roleId)) return;
    String action = appointed ? "任命部门管理员" : "撤销部门管理员";
    recorder.recordGlobal(operator, action, action, OperationTarget.globalUser(user));
  }

  /** 组装用户详情：所属部门取自部门树，业务角色按 ID 升序。 */
  private static UserResult view(User user, DepartmentTree tree, RoleHierarchy hierarchy) {
    Department department = tree.find(user.getDepartmentId()).orElseThrow();
    return new UserResult(
        user.getId(),
        user.getUsername(),
        user.getName(),
        new DepartmentRefResult(department.getId(), department.getName()),
        user.getStatus(),
        user.isDepartmentAdministrator(hierarchy),
        user.businessRoleIds(hierarchy).stream()
            .map(roleId -> new RoleRefResult(roleId, hierarchy.nameOf(roleId)))
            .toList());
  }

  /** 用户不存在。 */
  private static DomainException notFound() {
    return new DomainException(NOT_FOUND, "用户不存在。");
  }
}
