package com.rbac.rbac1.service;

import static com.rbac.rbac1.service.ManagementFailure.Reason.FORBIDDEN;
import static com.rbac.rbac1.service.ManagementFailure.Reason.INVALID_REQUEST;
import static com.rbac.rbac1.service.ManagementFailure.Reason.NOT_FOUND;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.rbac.rbac1.dto.UserCreateDTO;
import com.rbac.rbac1.dto.UserUpdateDTO;
import com.rbac.rbac1.entity.Department;
import com.rbac.rbac1.entity.OperationLogTarget;
import com.rbac.rbac1.entity.Role;
import com.rbac.rbac1.entity.UserAccount;
import com.rbac.rbac1.entity.UserRole;
import com.rbac.rbac1.mapper.RoleMapper;
import com.rbac.rbac1.mapper.RoleMapper.UserRoleRow;
import com.rbac.rbac1.mapper.SessionMapper;
import com.rbac.rbac1.mapper.UserMapper;
import com.rbac.rbac1.mapper.UserRoleMapper;
import com.rbac.rbac1.vo.DepartmentRefVO;
import com.rbac.rbac1.vo.PageVO;
import com.rbac.rbac1.vo.RoleRefVO;
import com.rbac.rbac1.vo.UserCreatedVO;
import com.rbac.rbac1.vo.UserVO;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户管理：管理员在管理范围内新增、查询和修改用户；调动部门和任免部门管理员只限总管理员。
 *
 * <p>停用账号时立即撤销该用户的全部会话。
 */
@Service
@RequiredArgsConstructor
public class UserService {

  private static final String USERNAME_UNIQUE = "uq_user_username";

  private static final String USERNAME_TAKEN = "该用户名已存在。";

  private final UserMapper users;

  private final DepartmentService departments;

  private final RoleMapper roles;

  private final UserRoleMapper userRoles;

  private final SessionMapper sessions;

  private final PasswordEncoder passwords;

  private final OperationLogService operationLogs;

  /**
   * 分页查询管理范围内的用户。
   *
   * @param actor 当前操作人，必须是管理员
   * @param departmentId 只查该部门及其下级部门，可为 {@code null}
   * @param keyword 匹配姓名或用户名，空串表示不过滤
   * @param includeDepartmentName 关键字是否也匹配部门名称
   * @param page 页码，从 1 开始
   * @param pageSize 每页条数
   * @return 按 ID 排序的一页用户
   * @throws ManagementFailure 部门不存在或超出管理范围
   */
  @Transactional(readOnly = true)
  public PageVO<UserVO> list(
      SessionUser actor,
      Long departmentId,
      String keyword,
      boolean includeDepartmentName,
      int page,
      int pageSize) {
    Map<Long, Department> scope =
        departments.managedDepartments(actor, departmentId).stream()
            .collect(Collectors.toMap(department -> department.getId(), Function.identity()));
    if (scope.isEmpty()) {
      return new PageVO<>(List.of(), page, pageSize, 0);
    }

    Page<UserAccount> matches =
        users.search(
            scope.keySet(), keyword, includeDepartmentName, PageRequest.of(page - 1, pageSize));
    List<Long> ids = matches.stream().map(user -> user.getId()).toList();
    Map<Long, List<UserRoleRow>> assigned =
        ids.isEmpty()
            ? Map.<Long, List<UserRoleRow>>of()
            : roles.findAssignedRoles(ids).stream()
                .collect(Collectors.groupingBy(UserRoleRow::getUserId));
    List<UserVO> items =
        matches.stream()
            .map(
                user ->
                    view(
                        user,
                        scope.get(user.getDepartmentId()),
                        assigned.getOrDefault(user.getId(), List.of())))
            .toList();
    return new PageVO<>(items, page, pageSize, matches.getTotalElements());
  }

  /**
   * 查询管理范围内的用户详情。
   *
   * @param actor 当前操作人，必须是管理员
   * @param id 用户 ID
   * @return 用户详情
   * @throws ManagementFailure 用户不存在为 NOT_FOUND，超出管理范围为 OUTSIDE_MANAGEMENT_SCOPE
   */
  @Transactional(readOnly = true)
  public UserVO get(SessionUser actor, long id) {
    UserAccount user = requireUser(actor, id);
    Department department = departments.requireManagedDepartment(actor, user.getDepartmentId());
    return view(user, department, roles.findAssignedRoles(List.of(id)));
  }

  /**
   * 在管理范围内的部门中新增用户，密码保存为摘要。
   *
   * @param actor 当前操作人，必须是管理员；同时任免部门管理员时必须是总管理员
   * @param request 用户资料和初始密码
   * @return 新增的用户
   * @throws ManagementFailure 部门超出管理范围、无权任免部门管理员或用户名重复
   */
  @Transactional
  public UserCreatedVO create(SessionUser actor, UserCreateDTO request) {
    Department department = departments.requireManagedDepartment(actor, request.getDepartmentId());
    requireManagementAuthority(actor, request.getDepartmentAdministrator() != null);

    UserAccount user = CloneFactory.copy(new UserAccount(), request);
    user.setPasswordHash(passwords.encode(request.getInitialPassword()));
    DuplicateKeys.saveAndFlush(users, user, USERNAME_UNIQUE, USERNAME_TAKEN);

    operationLogs.recordUserChange(
        actor, "新增用户", "初始状态为" + user.getStatus(), user, department.getId());
    if (Boolean.TRUE.equals(request.getDepartmentAdministrator())) {
      setDepartmentAdministrator(actor, user, true);
    }
    return new UserCreatedVO(
        view(user, department, roles.findAssignedRoles(List.of(user.getId()))));
  }

  /**
   * 部分修改用户资料、状态、所属部门和部门管理员身份。
   *
   * <p>先锁定用户行再修改，避免并发修改互相覆盖。普通资料和状态变更按原部门写入用户范围的记录，
   * 让原部门管理员能看到；跨部门调动写入全局记录。
   *
   * @param actor 当前操作人，必须是管理员；调动部门或任免部门管理员时必须是总管理员
   * @param id 用户 ID
   * @param request 要修改的字段，至少一项
   * @return 修改后的用户
   * @throws ManagementFailure 用户不存在、超出管理范围、无权调动、没有任何字段或用户名重复
   */
  @Transactional
  public UserVO update(SessionUser actor, long id, UserUpdateDTO request) {
    DepartmentService.requireAdministrator(actor);
    UserAccount user =
        users.findOrganizationUsersForUpdate(List.of(id)).stream()
            .findFirst()
            .orElseThrow(() -> new ManagementFailure(NOT_FOUND, "用户不存在。"));
    Department originalDepartment = departments.requireManagedDepartment(actor, user.getDepartmentId());
    requireManagementAuthority(
        actor, request.getDepartmentId() != null || request.getDepartmentAdministrator() != null);
    if (request.getUsername() == null
        && request.getName() == null
        && request.getStatus() == null
        && request.getDepartmentId() == null
        && request.getDepartmentAdministrator() == null) {
      throw new ManagementFailure(INVALID_REQUEST, "请填写要修改的用户信息。");
    }

    Department department =
        request.getDepartmentId() == null
                || request.getDepartmentId().equals(originalDepartment.getId())
            ? originalDepartment
            : departments.requireManagedDepartment(actor, request.getDepartmentId());
    List<String> changes = new ArrayList<>();
    if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
      changes.add("用户名从「" + user.getUsername() + "」改为「" + request.getUsername() + "」");
      user.setUsername(request.getUsername());
      DuplicateKeys.saveAndFlush(users, user, USERNAME_UNIQUE, USERNAME_TAKEN);
    }
    if (request.getName() != null && !request.getName().equals(user.getName())) {
      changes.add("姓名从「" + user.getName() + "」改为「" + request.getName() + "」");
      user.setName(request.getName());
    }
    if (!changes.isEmpty()) {
      operationLogs.recordUserChange(
          actor, "修改用户", String.join("，", changes), user, originalDepartment.getId());
    }
    if (request.getStatus() != null && !request.getStatus().equals(user.getStatus())) {
      boolean disabled = UserAccount.DISABLED.equals(request.getStatus());
      String detail = "状态从 " + user.getStatus() + " 改为 " + request.getStatus();
      user.setStatus(request.getStatus());
      if (disabled) {
        sessions.revokeAllForUser(id);
      }
      operationLogs.recordUserChange(
          actor, disabled ? "停用账号" : "启用账号", detail, user, originalDepartment.getId());
    }
    if (!department.getId().equals(originalDepartment.getId())) {
      user.setDepartmentId(department.getId());
      operationLogs.recordGlobalChange(
          actor,
          "跨部门调动",
          "从「" + originalDepartment.getName() + "」调至「" + department.getName() + "」",
          OperationLogTarget.USER,
          id,
          user.getName());
    }
    if (request.getDepartmentAdministrator() != null) {
      setDepartmentAdministrator(actor, user, request.getDepartmentAdministrator());
    }
    return view(user, department, roles.findAssignedRoles(List.of(id)));
  }

  /** 查找属于某个部门的用户；不属于任何部门的账号（如总管理员）视为不存在。 */
  private UserAccount requireUser(SessionUser actor, long id) {
    DepartmentService.requireAdministrator(actor);
    return users
        .findById(id)
        .filter(user -> user.getDepartmentId() != null)
        .orElseThrow(() -> new ManagementFailure(NOT_FOUND, "用户不存在。"));
  }

  /** 调动部门或任免部门管理员时要求操作人是总管理员。 */
  private static void requireManagementAuthority(SessionUser actor, boolean changingManagement) {
    if (changingManagement && !DepartmentService.isTotalAdmin(actor)) {
      throw new ManagementFailure(FORBIDDEN, "只有总管理员可以调动用户或任免部门管理员。");
    }
  }

  /** 任命或撤销部门管理员；身份未变化时不做任何事，也不写操作记录。 */
  private void setDepartmentAdministrator(SessionUser actor, UserAccount user, boolean enabled) {
    Role role = roles.findByRoleType(Role.DEPARTMENT_ADMIN);
    UserRole.Key key = new UserRole.Key(user.getId(), role.getId());
    if (userRoles.existsById(key) == enabled) {
      return;
    }
    if (enabled) {
      UserRole assignment = new UserRole();
      assignment.setUserId(user.getId());
      assignment.setRoleId(role.getId());
      userRoles.save(assignment);
    } else {
      userRoles.deleteById(key);
    }
    String action = enabled ? "任命部门管理员" : "撤销部门管理员";
    operationLogs.recordGlobalChange(
        actor, action, action, OperationLogTarget.USER, user.getId(), user.getName());
  }

  /** 组装用户响应：只列出业务角色，部门管理员身份单独标出。 */
  private static UserVO view(
      UserAccount user, Department department, List<UserRoleRow> assigned) {
    List<RoleRefVO> businessRoles =
        assigned.stream()
            .filter(role -> Role.BUSINESS.equals(role.getRoleType()))
            .map(role -> new RoleRefVO(role.getId(), role.getName()))
            .toList();
    boolean departmentAdmin =
        assigned.stream().anyMatch(role -> Role.DEPARTMENT_ADMIN.equals(role.getRoleType()));
    return new UserVO(
        user.getId(),
        user.getUsername(),
        user.getName(),
        new DepartmentRefVO(department.getId(), department.getName()),
        user.getStatus(),
        departmentAdmin,
        businessRoles);
  }
}
