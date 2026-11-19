package com.rbac.rbac1.service;

import static com.rbac.rbac1.service.DepartmentService.isTotalAdmin;
import static com.rbac.rbac1.service.DepartmentService.requireAdministrator;
import static com.rbac.rbac1.service.ManagementFailure.Reason.INVALID_REQUEST;
import static com.rbac.rbac1.service.ManagementFailure.Reason.NOT_FOUND;
import static com.rbac.rbac1.service.ManagementFailure.Reason.OUTSIDE_MANAGEMENT_SCOPE;

import com.rbac.rbac1.dto.BatchRoleChangeDTO;
import com.rbac.rbac1.entity.Role;
import com.rbac.rbac1.entity.UserAccount;
import com.rbac.rbac1.entity.UserRole;
import com.rbac.rbac1.mapper.RoleMapper;
import com.rbac.rbac1.mapper.UserMapper;
import com.rbac.rbac1.mapper.UserRoleMapper;
import com.rbac.rbac1.vo.BatchRoleVO;
import com.rbac.rbac1.vo.UserRoleVO;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户授权：管理员在管理范围内为用户分配或撤销业务角色。
 *
 * <p>管理角色（总管理员、部门管理员）不在这里维护。授权变化后在同一事务内同步待审核权限申请的状态。
 */
@Service
@RequiredArgsConstructor
public class UserAuthorizationService {

  private final UserMapper users;

  private final DepartmentService departments;

  private final RoleMapper roles;

  private final UserRoleMapper assignments;

  private final OperationLogService operationLogs;

  private final RequestStatusService statuses;

  /**
   * 把用户的业务角色替换为给定集合，只增删发生变化的关联；没有变化时不写操作记录。
   *
   * @param actor 当前操作人，必须是管理员
   * @param userId 用户 ID
   * @param roleIds 新的业务角色 ID
   * @return 用户及替换后的角色 ID，升序
   * @throws ManagementFailure 用户或角色不存在为 NOT_FOUND，用户超出管理范围为 OUTSIDE_MANAGEMENT_SCOPE，
   *     包含非业务角色为 INVALID_REQUEST
   */
  @Transactional
  public UserRoleVO replace(SessionUser actor, long userId, List<Long> roleIds) {
    UserAccount user = lockTargets(actor, List.of(userId)).getFirst();
    requireBusinessRoles(roleIds);
    Set<Long> requested = Set.copyOf(roleIds);
    List<UserRole> existing = assignments.findBusinessAssignments(List.of(userId));
    Set<Long> existingIds = existing.stream().map(link -> link.getRoleId()).collect(Collectors.toSet());
    List<Long> added = roleIds.stream().filter(id -> !existingIds.contains(id)).toList();
    List<UserRole> removed =
        existing.stream().filter(link -> !requested.contains(link.getRoleId())).toList();

    assignments.deleteAll(removed);
    assignments.saveAll(added.stream().map(id -> assignment(userId, id)).toList());
    if (!added.isEmpty() || !removed.isEmpty()) {
      List<String> detail = new ArrayList<>();
      if (!added.isEmpty()) {
        detail.add("分配角色 ID " + added);
      }
      if (!removed.isEmpty()) {
        detail.add("撤销角色 ID " + removed.stream().map(link -> link.getRoleId()).toList());
      }
      operationLogs.recordUserChange(
          actor, "修改用户授权", String.join("，", detail), user, user.getDepartmentId());
    }
    statuses.settleUsers(List.of(userId));
    return new UserRoleVO(userId, roleIds.stream().sorted().toList());
  }

  /**
   * 为多个用户批量分配（GRANT）或撤销业务角色。
   *
   * <p>只统计实际发生变化的用户，操作记录为每个受影响的用户保存快照。
   *
   * @param actor 当前操作人，必须是管理员
   * @param request 动作、用户 ID 和角色 ID
   * @return 动作和受影响的用户数
   * @throws ManagementFailure 用户或角色不存在、用户超出管理范围或包含非业务角色
   */
  @Transactional
  public BatchRoleVO batch(SessionUser actor, BatchRoleChangeDTO request) {
    List<UserAccount> targets = lockTargets(actor, request.userIds());
    List<Role> businessRoles = requireBusinessRoles(request.roleIds());
    Set<Long> selected = Set.copyOf(request.roleIds());
    Map<Long, List<UserRole>> existing =
        assignments.findBusinessAssignments(request.userIds()).stream()
            .collect(Collectors.groupingBy(link -> link.getUserId()));
    boolean grant = "GRANT".equals(request.action());
    List<UserRole> added = new ArrayList<>();
    List<UserRole> removed = new ArrayList<>();
    List<UserAccount> affected = new ArrayList<>();
    for (UserAccount user : targets) {
      List<UserRole> links = existing.getOrDefault(user.getId(), List.of());
      if (grant) {
        Set<Long> current = links.stream().map(link -> link.getRoleId()).collect(Collectors.toSet());
        List<Long> missing = request.roleIds().stream().filter(id -> !current.contains(id)).toList();
        if (!missing.isEmpty()) {
          missing.forEach(id -> added.add(assignment(user.getId(), id)));
          affected.add(user);
        }
      } else {
        List<UserRole> revoked = links.stream().filter(link -> selected.contains(link.getRoleId())).toList();
        if (!revoked.isEmpty()) {
          removed.addAll(revoked);
          affected.add(user);
        }
      }
    }

    assignments.deleteAll(removed);
    assignments.saveAll(added);
    if (!affected.isEmpty()) {
      String action = grant ? "批量分配角色" : "批量撤销角色";
      String detail =
          "所选角色："
              + String.join("、", businessRoles.stream().map(role -> role.getName()).sorted().toList());
      operationLogs.recordUserChanges(actor, action, detail, affected);
    }
    statuses.settleUsers(affected.stream().map(user -> user.getId()).toList());
    return new BatchRoleVO(request.action(), affected.size());
  }

  /**
   * 锁定目标用户行并校验都在操作人的管理范围内；不属于任何部门的账号视为不存在。
   */
  private List<UserAccount> lockTargets(SessionUser actor, List<Long> userIds) {
    requireAdministrator(actor);
    List<UserAccount> targets = users.findOrganizationUsersForUpdate(userIds);
    if (targets.size() != userIds.size()) {
      throw new ManagementFailure(NOT_FOUND, "所选用户中有用户不存在。");
    }
    if (!isTotalAdmin(actor)) {
      Set<Long> scope =
          departments.managedDepartments(actor, null).stream()
              .map(department -> department.getId())
              .collect(Collectors.toSet());
      if (targets.stream().anyMatch(user -> !scope.contains(user.getDepartmentId()))) {
        throw new ManagementFailure(OUTSIDE_MANAGEMENT_SCOPE, "请选择本部门或下级部门内的用户。");
      }
    }
    return targets;
  }

  /** 要求所选角色都存在且都是业务角色。 */
  private List<Role> requireBusinessRoles(List<Long> roleIds) {
    List<Role> selected = roles.findAllById(roleIds);
    if (selected.size() != roleIds.size()) {
      throw new ManagementFailure(NOT_FOUND, "所选角色中有角色不存在。");
    }
    if (selected.stream().anyMatch(role -> !Role.BUSINESS.equals(role.getRoleType()))) {
      throw new ManagementFailure(INVALID_REQUEST, "这里只能分配或撤销业务角色。");
    }
    return selected;
  }

  /** 组装一条用户角色关联。 */
  private static UserRole assignment(long userId, long roleId) {
    UserRole assignment = new UserRole();
    assignment.setUserId(userId);
    assignment.setRoleId(roleId);
    return assignment;
  }
}
