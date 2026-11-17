package com.rbac.rbac1ddd.application.role;

import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.FORBIDDEN;
import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.NOT_FOUND;

import com.rbac.rbac1ddd.application.request.PermissionRequestSettlement;
import com.rbac.rbac1ddd.application.shared.OperationRecorder;
import com.rbac.rbac1ddd.application.shared.PageResult;
import com.rbac.rbac1ddd.application.shared.OperationTarget;
import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.domain.permission.PermissionCatalog;
import com.rbac.rbac1ddd.domain.permission.PermissionRepository;
import com.rbac.rbac1ddd.domain.role.Role;
import com.rbac.rbac1ddd.domain.role.RoleChange;
import com.rbac.rbac1ddd.domain.role.RoleHierarchy;
import com.rbac.rbac1ddd.domain.role.RoleRepository;
import com.rbac.rbac1ddd.domain.role.RoleType;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.shared.DomainPage;
import com.rbac.rbac1ddd.domain.user.UserRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用服务：角色相关用例。
 *
 * <p>只负责编排：检查操作者身份、开启事务、通过仓储取出聚合、调用聚合的业务方法、保存聚合、写操作记录。
 * 继承、循环、按钮菜单、删除等业务规则都在 {@link Role} 和 {@link RoleHierarchy} 中（GRASP 控制器）。
 */
@Service
@RequiredArgsConstructor
public class RoleApplicationService {
  private final RoleRepository roles;
  private final PermissionRepository permissions;
  private final UserRepository users;
  private final RoleDetailsAssembler assembler;
  private final OperationRecorder recorder;
  private final PermissionRequestSettlement settlement;

  /**
   * 分页查询角色。部门管理员只能查询业务角色。
   *
   * @param keyword 名称关键字，可为空
   * @param type 角色类型筛选，可为空；部门管理员未指定时按业务角色查询
   */
  @Transactional(readOnly = true)
  public PageResult<RoleSummaryResult> list(
      Operator operator, int page, int pageSize, String keyword, RoleType type) {
    operator.requireAdministrator();
    if (!operator.isTotalAdmin()) {
      if (type != null && type != RoleType.BUSINESS)
        throw new DomainException(FORBIDDEN, "部门管理员只能查询业务角色。");
      type = RoleType.BUSINESS;
    }
    DomainPage<Role> result = roles.search(keyword, type, page, pageSize);
    RoleHierarchy hierarchy = roles.loadHierarchy();
    PermissionCatalog catalog = permissions.loadCatalog();
    return new PageResult<>(
        result.items().stream().map(role -> assembler.summary(role, hierarchy, catalog)).toList(),
        page,
        pageSize,
        result.total());
  }

  /** 查询角色详情。部门管理员只能查询业务角色。 */
  @Transactional(readOnly = true)
  public RoleDetailsResult get(Operator operator, long roleId) {
    operator.requireAdministrator();
    Role role = roles.findById(roleId).orElseThrow(() -> notFound("角色不存在。"));
    if (!operator.isTotalAdmin() && !role.isBusiness())
      throw new DomainException(FORBIDDEN, "部门管理员只能查询业务角色。");
    return details(role);
  }

  /** 总管理员新增业务角色，同时配置直接权限和继承角色，并写入全局操作记录。 */
  @Transactional
  public RoleDetailsResult create(Operator operator, RoleCommand command) {
    operator.requireTotalAdmin();
    RoleHierarchy hierarchy = roles.loadHierarchy();
    Role role =
        Role.createBusiness(command.toConfiguration(), hierarchy, permissions.loadCatalog());
    roles.save(role);
    recorder.recordGlobal(
        operator,
        "新增角色",
        "配置直接权限 ID "
            + new TreeSet<>(command.permissionIds())
            + "，继承角色 "
            + roleNames(hierarchy, command.inheritedRoleIds()),
        OperationTarget.role(role));
    return details(role);
  }

  /**
   * 总管理员修改业务角色。
   *
   * <p>直接权限和继承关系一起校验、在同一事务中保存；形成循环时整次操作失败，原配置不变。
   * 配置有变化才写操作记录；本角色及其后代角色的持有人都重新结算待审核申请。
   */
  @Transactional
  public RoleDetailsResult update(Operator operator, long roleId, RoleCommand command) {
    operator.requireTotalAdmin();
    Role role = roles.findForUpdate(roleId).orElseThrow(() -> notFound("业务角色不存在。"));
    RoleHierarchy hierarchy = roles.loadHierarchy();
    Set<Long> affectedRoles = hierarchy.descendantsIncluding(roleId);
    RoleChange change =
        role.reconfigure(command.toConfiguration(), hierarchy, permissions.loadCatalog());
    roles.save(role);
    if (!change.isEmpty())
      recorder.recordGlobal(
          operator, "修改角色", describe(change, hierarchy), OperationTarget.role(role));
    settlement.settle(users.findIdsHoldingAny(affectedRoles));
    return details(role);
  }

  /** 总管理员删除角色并清理其权限关联，保留权限定义。 */
  @Transactional
  public void delete(Operator operator, long roleId) {
    operator.requireTotalAdmin();
    Role role = roles.findForUpdate(roleId).orElseThrow(() -> notFound("业务角色不存在。"));
    role.ensureDeletable(users.countHolding(roleId), roles.loadHierarchy());
    roles.delete(role);
    recorder.recordGlobal(
        operator, "删除角色", "删除无用户关联且无继承关系的业务角色", OperationTarget.role(role));
  }

  /** 按保存后的最新继承关系组装角色详情。 */
  private RoleDetailsResult details(Role role) {
    return assembler.details(
        role, roles.loadHierarchy(), permissions.loadCatalog(), users.countHolding(role.getId()));
  }

  /** 把角色变更描述为操作记录的中文明细，只列出实际变化的部分。 */
  private static String describe(RoleChange change, RoleHierarchy hierarchy) {
    List<String> parts = new ArrayList<>();
    if (change.nameChanged())
      parts.add("名称从「" + change.oldName() + "」改为「" + change.newName() + "」");
    if (change.descriptionChanged())
      parts.add("说明从「" + change.oldDescription() + "」改为「" + change.newDescription() + "」");
    if (change.permissionsChanged())
      parts.add(
          "直接权限从 "
              + new TreeSet<>(change.oldPermissionIds())
              + " 改为 "
              + new TreeSet<>(change.newPermissionIds()));
    if (change.parentsChanged())
      parts.add(
          "继承角色从 "
              + roleNames(hierarchy, change.oldParentRoleIds())
              + " 改为 "
              + roleNames(hierarchy, change.newParentRoleIds()));
    return String.join("，", parts);
  }

  /** 把角色 ID 列表格式化为「名称（ID=n）」并用顿号连接；空列表写作「无」。 */
  private static String roleNames(RoleHierarchy hierarchy, Collection<Long> ids) {
    return ids.isEmpty()
        ? "无"
        : RoleDetailsAssembler.refs(hierarchy, ids).stream()
            .map(role -> role.name() + "（ID=" + role.id() + "）")
            .collect(Collectors.joining("、"));
  }

  /** 构造资源不存在的业务异常。 */
  private static DomainException notFound(String detail) {
    return new DomainException(NOT_FOUND, detail);
  }
}
