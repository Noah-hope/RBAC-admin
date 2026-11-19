package com.rbac.rbac1.service;

import static com.rbac.rbac1.service.DepartmentService.isTotalAdmin;
import static com.rbac.rbac1.service.DepartmentService.requireAdministrator;
import static com.rbac.rbac1.service.DepartmentService.requireTotalAdmin;
import static com.rbac.rbac1.service.ManagementFailure.Reason.CONFLICT;
import static com.rbac.rbac1.service.ManagementFailure.Reason.FORBIDDEN;
import static com.rbac.rbac1.service.ManagementFailure.Reason.INVALID_REQUEST;
import static com.rbac.rbac1.service.ManagementFailure.Reason.NOT_FOUND;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.rbac.rbac1.dto.RoleDTO;
import com.rbac.rbac1.entity.OperationLogTarget;
import com.rbac.rbac1.entity.Permission;
import com.rbac.rbac1.entity.Role;
import com.rbac.rbac1.entity.RoleInheritance;
import com.rbac.rbac1.entity.RolePermission;
import com.rbac.rbac1.mapper.RoleInheritanceMapper;
import com.rbac.rbac1.mapper.RoleMapper;
import com.rbac.rbac1.mapper.RolePermissionMapper;
import com.rbac.rbac1.mapper.UserRoleMapper;
import com.rbac.rbac1.service.RolePermissionService.Graph;
import com.rbac.rbac1.vo.PageVO;
import com.rbac.rbac1.vo.RoleDetailsVO;
import com.rbac.rbac1.vo.RoleRefVO;
import com.rbac.rbac1.vo.RoleSummaryVO;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色管理：总管理员维护业务角色的直接权限和继承关系；预置管理角色只读。
 *
 * <p>保存前在内存关系图上校验最终配置：不能循环继承，不能重复直接配置已继承的权限，
 * 角色自身及全部后代角色的按钮都必须同时拥有所属菜单。
 */
@Service
@RequiredArgsConstructor
public class RoleService {

  private static final String ROLE_NAME_UNIQUE = "uq_role_name";

  private static final String ROLE_NAME_TAKEN = "该角色名称已存在。";

  private final RoleMapper roles;

  private final RolePermissionMapper grants;

  private final RoleInheritanceMapper inheritance;

  private final UserRoleMapper assignments;

  private final RolePermissionService effective;

  private final RequestStatusService statuses;

  private final OperationLogService logs;

  /**
   * 分页查询角色。部门管理员只能查询业务角色。
   *
   * @param actor 当前操作人，必须是管理员
   * @param page 页码，从 1 开始
   * @param pageSize 每页条数
   * @param keyword 匹配角色名称，空串表示不过滤
   * @param roleType 只查该类型的角色，可为 {@code null}
   * @return 按 ID 排序的一页角色摘要
   * @throws ManagementFailure 部门管理员查询管理角色时为 FORBIDDEN
   */
  @Transactional(readOnly = true)
  public PageVO<RoleSummaryVO> list(
      SessionUser actor, int page, int pageSize, String keyword, String roleType) {
    requireAdministrator(actor);
    if (!isTotalAdmin(actor)) {
      if (roleType != null && !Role.BUSINESS.equals(roleType)) {
        throw new ManagementFailure(FORBIDDEN, "部门管理员只能查询业务角色。");
      }
      roleType = Role.BUSINESS;
    }
    Page<Role> matches = roles.searchRoles(keyword, roleType, PageRequest.of(page - 1, pageSize));
    Graph graph = effective.load();
    return new PageVO<>(
        matches.stream().map(role -> effective.summary(graph, role)).toList(),
        page,
        pageSize,
        matches.getTotalElements());
  }

  /**
   * 查询角色详情。部门管理员只能查询业务角色。
   *
   * @param actor 当前操作人，必须是管理员
   * @param roleId 角色 ID
   * @return 角色详情
   * @throws ManagementFailure 角色不存在为 NOT_FOUND，部门管理员查询管理角色为 FORBIDDEN
   */
  @Transactional(readOnly = true)
  public RoleDetailsVO get(SessionUser actor, long roleId) {
    requireAdministrator(actor);
    Role role =
        roles.findById(roleId).orElseThrow(() -> new ManagementFailure(NOT_FOUND, "角色不存在。"));
    if (!isTotalAdmin(actor) && !Role.BUSINESS.equals(role.getRoleType())) {
      throw new ManagementFailure(FORBIDDEN, "部门管理员只能查询业务角色。");
    }
    return details(role);
  }

  /**
   * 新增业务角色。
   *
   * @param actor 当前操作人，必须是总管理员
   * @param input 角色名称、说明、直接权限和继承的角色
   * @return 新增的角色详情
   * @throws ManagementFailure 配置不合法为 INVALID_REQUEST，名称重复为 DUPLICATE
   */
  @Transactional
  public RoleDetailsVO create(SessionUser actor, RoleDTO input) {
    requireTotalAdmin(actor);
    List<RoleRefVO> selectedParents = validateNew(input);
    Role role = CloneFactory.copy(new Role(), input);
    role.setRoleType(Role.BUSINESS);
    DuplicateKeys.saveAndFlush(roles, role, ROLE_NAME_UNIQUE, ROLE_NAME_TAKEN);
    savePermissions(role.getId(), input.getPermissionIds());
    saveParents(role.getId(), input.getInheritedRoleIds());
    logs.recordGlobalChange(
        actor,
        "新增角色",
        "配置直接权限 ID " + input.getPermissionIds() + "，继承角色 " + roleNames(selectedParents),
        OperationLogTarget.ROLE,
        role.getId(),
        role.getName());
    return details(role);
  }

  /**
   * 整体替换业务角色的名称、说明、直接权限和继承关系。
   *
   * <p>修改会影响全部后代角色，因此先在关系图上校验自身及后代的最终配置，通过后才只增删变化的关联；
   * 最后同步受影响用户的待审核权限申请。
   *
   * @param actor 当前操作人，必须是总管理员
   * @param roleId 角色 ID
   * @param input 新的完整配置
   * @return 修改后的角色详情
   * @throws ManagementFailure 角色不存在为 NOT_FOUND，管理角色为 FORBIDDEN，配置不合法为 INVALID_REQUEST，
   *     名称重复为 DUPLICATE
   */
  @Transactional
  public RoleDetailsVO update(SessionUser actor, long roleId, RoleDTO input) {
    requireTotalAdmin(actor);
    Role role = requireEditableRole(roleId);
    Graph graph = effective.load();
    Set<Long> existing = graph.direct().getOrDefault(roleId, Set.of());
    validateSelection(graph, input, existing);
    if (effective.ancestors(graph, input.getInheritedRoleIds()).contains(roleId)) {
      throw new ManagementFailure(INVALID_REQUEST, "不能继承自身或形成循环继承。");
    }
    Set<Long> affected = effective.descendants(graph, roleId);
    Set<Long> previousParents = graph.parents().getOrDefault(roleId, Set.of());
    // 先校验最终配置对自身及后代的影响，通过后才保存关联。
    graph.direct().put(roleId, new TreeSet<>(input.getPermissionIds()));
    graph.parents().put(roleId, new TreeSet<>(input.getInheritedRoleIds()));
    for (Long id : affected) {
      validateMenus(graph, effective.effectiveIds(graph, id), graph.roles().get(id).getName());
    }

    Set<Long> selected = Set.copyOf(input.getPermissionIds());
    grants.deleteAll(
        grants.findByRoleIdOrderByPermissionIdAsc(roleId).stream()
            .filter(link -> !selected.contains(link.getPermissionId()))
            .toList());
    savePermissions(
        roleId, input.getPermissionIds().stream().filter(id -> !existing.contains(id)).toList());
    Set<Long> parents = Set.copyOf(input.getInheritedRoleIds());
    inheritance.deleteAll(
        previousParents.stream()
            .filter(id -> !parents.contains(id))
            .map(
                id -> {
                  RoleInheritance link = new RoleInheritance();
                  link.setRoleId(roleId);
                  link.setInheritedRoleId(id);
                  return link;
                })
            .toList());
    saveParents(
        roleId,
        input.getInheritedRoleIds().stream().filter(id -> !previousParents.contains(id)).toList());
    List<String> changes = new ArrayList<>();
    boolean renamed = !role.getName().equals(input.getName());
    if (renamed) {
      changes.add("名称从「" + role.getName() + "」改为「" + input.getName() + "」");
    }
    if (!role.getDescription().equals(input.getDescription())) {
      changes.add("说明从「" + role.getDescription() + "」改为「" + input.getDescription() + "」");
    }
    if (!existing.equals(selected)) {
      changes.add("直接权限从 " + existing + " 改为 " + selected);
    }
    if (!previousParents.equals(parents)) {
      changes.add(
          "继承角色从 "
              + roleNames(effective.references(graph, previousParents))
              + " 改为 "
              + roleNames(effective.references(graph, parents)));
    }
    role.setName(input.getName());
    role.setDescription(input.getDescription());
    if (renamed) {
      DuplicateKeys.saveAndFlush(roles, role, ROLE_NAME_UNIQUE, ROLE_NAME_TAKEN);
    }
    if (!changes.isEmpty()) {
      logs.recordGlobalChange(
          actor,
          "修改角色",
          String.join("，", changes),
          OperationLogTarget.ROLE,
          role.getId(),
          role.getName());
    }
    statuses.settleUsers(
        assignments.findByRoleIdIn(affected).stream().map(a -> a.getUserId()).distinct().toList());
    return details(role);
  }

  /**
   * 删除业务角色。仍有用户关联或仍处于继承关系中的角色不能删除。
   *
   * @param actor 当前操作人，必须是总管理员
   * @param roleId 角色 ID
   * @throws ManagementFailure 角色不存在为 NOT_FOUND，管理角色为 FORBIDDEN，仍有关联为 CONFLICT
   */
  @Transactional
  public void delete(SessionUser actor, long roleId) {
    requireTotalAdmin(actor);
    Role role = requireEditableRole(roleId);
    long count = assignments.countByRoleId(roleId);
    if (count > 0) {
      throw new ManagementFailure(CONFLICT, "该角色仍关联 " + count + " 个用户，请先撤销用户关联。");
    }
    if (inheritance.existsByRoleIdOrInheritedRoleId(roleId, roleId)) {
      throw new ManagementFailure(CONFLICT, "该角色仍存在父角色或子角色，请先解除继承关系。");
    }
    grants.deleteAll(grants.findByRoleIdOrderByPermissionIdAsc(roleId));
    roles.delete(role);
    logs.recordGlobalChange(
        actor,
        "删除角色",
        "删除无用户关联且无继承关系的业务角色",
        OperationLogTarget.ROLE,
        role.getId(),
        role.getName());
  }

  /**
   * 校验一个待建角色的配置，新增角色和审核新建角色申请时共用。
   *
   * @param input 待建角色的配置
   * @return 继承的角色引用，升序
   * @throws ManagementFailure 权限或父角色不存在、重复配置已继承的权限或按钮缺少所属菜单时为 INVALID_REQUEST
   */
  @Transactional(readOnly = true)
  public List<RoleRefVO> validateNew(RoleDTO input) {
    Graph graph = effective.load();
    validateSelection(graph, input, Set.of());
    TreeSet<Long> ids = new TreeSet<>(input.getPermissionIds());
    ids.addAll(effective.sources(graph, input.getInheritedRoleIds()).keySet());
    validateMenus(graph, ids, input.getName());
    return effective.references(graph, input.getInheritedRoleIds());
  }

  /**
   * 校验所选权限和父角色都存在，并且没有把已继承的权限新增为直接权限。
   *
   * <p>修改角色时，原本已有的直接权限即使后来也能继承得到，仍允许保留。
   */
  private void validateSelection(Graph graph, RoleDTO input, Set<Long> existingDirect) {
    if (!graph.permissions().keySet().containsAll(input.getPermissionIds())) {
      throw new ManagementFailure(INVALID_REQUEST, "选择的权限不存在。");
    }
    for (Long id : input.getInheritedRoleIds()) {
      Role parent = graph.roles().get(id);
      if (parent == null || !Role.BUSINESS.equals(parent.getRoleType())) {
        throw new ManagementFailure(INVALID_REQUEST, "父角色必须是当前存在的业务角色。");
      }
    }
    Set<Long> inherited = effective.sources(graph, input.getInheritedRoleIds()).keySet();
    for (Long id : input.getPermissionIds()) {
      if (!existingDirect.contains(id) && inherited.contains(id)) {
        throw new ManagementFailure(
            INVALID_REQUEST,
            "权限「" + graph.permissions().get(id).getName() + "」已通过继承获得，不能新增重复直接配置。");
      }
    }
  }

  /** 校验有效权限中的每个按钮都同时拥有其所属菜单。 */
  private void validateMenus(Graph graph, Set<Long> ids, String roleName) {
    for (Long id : ids) {
      Permission permission = graph.permissions().get(id);
      if (permission.getParentId() != null && !ids.contains(permission.getParentId())) {
        throw new ManagementFailure(
            INVALID_REQUEST,
            "角色「"
                + roleName
                + "」的按钮「"
                + permission.getName()
                + "」缺少所属菜单「"
                + graph.permissions().get(permission.getParentId()).getName()
                + "」。");
      }
    }
  }

  /** 操作记录中显示的角色列表，例如「角色A（ID=3）、角色B（ID=5）」，为空时显示「无」。 */
  private String roleNames(List<RoleRefVO> roles) {
    return roles.isEmpty()
        ? "无"
        : roles.stream()
            .map(role -> role.name() + "（ID=" + role.id() + "）")
            .collect(Collectors.joining("、"));
  }

  /** 锁定并返回可修改的业务角色；预置管理角色只读。 */
  private Role requireEditableRole(long id) {
    Role role =
        roles
            .findRoleForUpdate(id)
            .orElseThrow(() -> new ManagementFailure(NOT_FOUND, "业务角色不存在。"));
    if (!Role.BUSINESS.equals(role.getRoleType())) {
      throw new ManagementFailure(FORBIDDEN, "预置管理角色只读，不能修改或删除。");
    }
    return role;
  }

  /** 为角色新增直接权限关联。 */
  private void savePermissions(long roleId, List<Long> ids) {
    grants.saveAll(
        ids.stream()
            .map(
                id -> {
                  RolePermission link = new RolePermission();
                  link.setRoleId(roleId);
                  link.setPermissionId(id);
                  return link;
                })
            .toList());
  }

  /** 为角色新增继承关系。 */
  private void saveParents(long roleId, List<Long> ids) {
    inheritance.saveAll(
        ids.stream()
            .map(
                id -> {
                  RoleInheritance link = new RoleInheritance();
                  link.setRoleId(roleId);
                  link.setInheritedRoleId(id);
                  return link;
                })
            .toList());
  }

  /** 基于最新关系图组装角色详情。 */
  private RoleDetailsVO details(Role role) {
    return effective.details(effective.load(), role, assignments.countByRoleId(role.getId()));
  }
}
