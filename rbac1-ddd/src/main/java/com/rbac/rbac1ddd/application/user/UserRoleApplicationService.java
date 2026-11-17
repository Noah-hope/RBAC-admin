package com.rbac.rbac1ddd.application.user;

import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.NOT_FOUND;

import com.rbac.rbac1ddd.application.request.PermissionRequestSettlement;
import com.rbac.rbac1ddd.application.shared.OperationRecorder;
import com.rbac.rbac1ddd.application.shared.OperationTarget;
import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.domain.department.DepartmentRepository;
import com.rbac.rbac1ddd.domain.role.RoleHierarchy;
import com.rbac.rbac1ddd.domain.role.RoleRepository;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.user.RoleAssignmentChange;
import com.rbac.rbac1ddd.domain.user.User;
import com.rbac.rbac1ddd.domain.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 应用服务：用户角色关联。分配和解除在同一事务中完成，保存成功后立即生效。 */
@Service
@RequiredArgsConstructor
public class UserRoleApplicationService {
  private final UserRepository users;
  private final RoleRepository roles;
  private final DepartmentRepository departments;
  private final OperationRecorder recorder;
  private final PermissionRequestSettlement settlement;

  /**
   * 提交用户最终拥有的业务角色列表，只新增或解除发生变化的关联；空列表表示解除全部业务角色。
   *
   * <p>有变化才写操作记录；保存后结算该用户的待审核申请。
   */
  @Transactional
  public UserRoleResult replace(Operator operator, long userId, List<Long> roleIds) {
    User user = lockTargets(operator, List.of(userId)).getFirst();
    RoleHierarchy hierarchy = roles.loadHierarchy();
    RoleAssignmentChange change =
        user.replaceBusinessRoles(hierarchy.requireBusinessRoles(roleIds), hierarchy);
    users.save(user);
    if (!change.isEmpty()) {
      List<String> detail = new ArrayList<>();
      if (!change.added().isEmpty()) detail.add("分配角色 ID " + change.added());
      if (!change.removed().isEmpty()) detail.add("撤销角色 ID " + change.removed());
      recorder.recordUserScope(
          operator, "修改用户授权", String.join("，", detail), List.of(OperationTarget.user(user)));
    }
    settlement.settle(List.of(userId));
    return new UserRoleResult(userId, roleIds.stream().sorted().toList());
  }

  /**
   * 批量分配或撤销：先校验全部用户都在管理范围内，再整批保存。
   *
   * <p>只保存角色实际发生变化的用户，并只为这些用户写操作记录和结算申请。
   */
  @Transactional
  public BatchRoleResult batch(
      Operator operator, BatchAction action, List<Long> userIds, List<Long> roleIds) {
    List<User> targets = lockTargets(operator, userIds);
    RoleHierarchy hierarchy = roles.loadHierarchy();
    Set<Long> selected = hierarchy.requireBusinessRoles(roleIds);
    List<User> affected = new ArrayList<>();
    for (User user : targets) {
      RoleAssignmentChange change =
          action == BatchAction.GRANT
              ? user.grantBusinessRoles(selected)
              : user.revokeBusinessRoles(selected);
      if (!change.isEmpty()) {
        users.save(user);
        affected.add(user);
      }
    }
    if (!affected.isEmpty())
      recorder.recordUserScope(
          operator,
          action == BatchAction.GRANT ? "批量分配角色" : "批量撤销角色",
          "所选角色："
              + String.join(
                  "、", selected.stream().map(hierarchy::nameOf).sorted().toList()),
          affected.stream().map(OperationTarget::user).toList());
    settlement.settle(affected.stream().map(User::getId).toList());
    return new BatchRoleResult(action.name(), affected.size());
  }

  /**
   * 加行锁读取目标用户，并校验操作者是管理员、用户全部存在且都在管理范围内。
   *
   * @return 按 ID 排序的目标用户
   */
  private List<User> lockTargets(Operator operator, List<Long> userIds) {
    operator.requireAdministrator();
    List<User> targets = users.findOrganizationUsersForUpdate(userIds);
    if (targets.size() != userIds.size())
      throw new DomainException(NOT_FOUND, "所选用户中有用户不存在。");
    operator.scopeIn(departments.loadTree()).ensureCovers(targets);
    return targets;
  }
}
