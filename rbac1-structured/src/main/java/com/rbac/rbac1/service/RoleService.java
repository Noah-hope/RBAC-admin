package com.rbac.rbac1.service;

import static com.rbac.rbac1.service.DepartmentService.*;
import static com.rbac.rbac1.service.ManagementFailure.Reason.*;

import com.rbac.rbac1.dto.RoleDtos.*;
import com.rbac.rbac1.dto.SessionDtos.SessionUser;
import com.rbac.rbac1.entity.*;
import com.rbac.rbac1.mapper.*;
import com.rbac.rbac1.service.RolePermissionService.Graph;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleService {
  private final RoleMapper roles;
  private final PermissionMapper permissions;
  private final RolePermissionMapper grants;
  private final RoleInheritanceMapper inheritance;
  private final UserRoleMapper assignments;
  private final RolePermissionService effective;
  private final RequestStatusService statuses;
  private final OperationLogService logs;

  public RoleService(RoleMapper roles, PermissionMapper permissions, RolePermissionMapper grants,
      RoleInheritanceMapper inheritance, UserRoleMapper assignments, RolePermissionService effective,
      RequestStatusService statuses, OperationLogService logs) {
    this.roles=roles; this.permissions=permissions; this.grants=grants; this.inheritance=inheritance;
    this.assignments=assignments; this.effective=effective; this.statuses=statuses; this.logs=logs;
  }

  @Transactional(readOnly = true)
  public RolePage list(SessionUser actor, int page, int pageSize, String keyword, String roleType) {
    requireAdministrator(actor);
    if (!isTotalAdmin(actor)) {
      if (roleType != null && !"BUSINESS".equals(roleType))
        throw new ManagementFailure(FORBIDDEN,"部门管理员只能查询业务角色。");
      roleType="BUSINESS";
    }
    var matches=roles.searchRoles(keyword,roleType,PageRequest.of(page-1,pageSize));
    var graph=effective.load();
    return new RolePage(matches.stream().map(role -> effective.summary(graph,role)).toList(),
        page,pageSize,matches.getTotalElements());
  }

  @Transactional(readOnly = true)
  public RoleDetails get(SessionUser actor, long roleId) {
    requireAdministrator(actor);
    var role=roles.findById(roleId).orElseThrow(() -> new ManagementFailure(NOT_FOUND,"角色不存在。"));
    if (!isTotalAdmin(actor) && !"BUSINESS".equals(role.roleType))
      throw new ManagementFailure(FORBIDDEN,"部门管理员只能查询业务角色。");
    return details(role);
  }

  @Transactional
  public RoleDetails create(SessionUser actor, RoleInput input) {
    requireTotalAdmin(actor);
    validateNew(input);
    var role=new Role(); role.name=input.name; role.description=input.description; role.roleType="BUSINESS";
    roles.save(role);
    savePermissions(role.id,input.permissionIds);
    saveParents(role.id,input.inheritedRoleIds);
    logs.recordGlobalChange(actor,"新增角色", "配置直接权限 ID " + input.permissionIds
        + "，继承角色 ID " + input.inheritedRoleIds,"ROLE",role.id,role.name);
    return details(role);
  }

  @Transactional
  public RoleDetails update(SessionUser actor, long roleId, RoleInput input) {
    requireTotalAdmin(actor);
    var role=requireEditableRole(roleId);
    var graph=effective.load();
    var existing=graph.direct().getOrDefault(roleId,Set.of());
    validateSelection(graph,input,existing);
    if (effective.ancestors(graph,input.inheritedRoleIds).contains(roleId))
      throw new ManagementFailure(INVALID_REQUEST,"不能继承自身或形成循环继承。");
    var affected=effective.descendants(graph,roleId);
    var previousParents=graph.parents().getOrDefault(roleId,Set.of());
    graph.direct().put(roleId,new TreeSet<>(input.permissionIds));
    graph.parents().put(roleId,new TreeSet<>(input.inheritedRoleIds));
    for (var id : affected) validateMenus(graph,effective.effectiveIds(graph,id),graph.roles().get(id).name);

    var selected=Set.copyOf(input.permissionIds);
    grants.deleteAll(grants.findByRoleIdOrderByPermissionIdAsc(roleId).stream()
        .filter(link -> !selected.contains(link.permissionId)).toList());
    savePermissions(roleId,input.permissionIds.stream().filter(id -> !existing.contains(id)).toList());
    var parents=Set.copyOf(input.inheritedRoleIds);
    inheritance.deleteAll(previousParents.stream().filter(id -> !parents.contains(id))
        .map(id -> { var link=new RoleInheritance(); link.roleId=roleId; link.inheritedRoleId=id; return link; }).toList());
    saveParents(roleId,input.inheritedRoleIds.stream().filter(id -> !previousParents.contains(id)).toList());
    var changes=new ArrayList<String>();
    if (!role.name.equals(input.name)) changes.add("名称从「" + role.name + "」改为「" + input.name + "」");
    if (!role.description.equals(input.description)) changes.add("说明从「" + role.description + "」改为「" + input.description + "」");
    if (!existing.equals(selected)) changes.add("直接权限从 " + existing + " 改为 " + selected);
    if (!previousParents.equals(parents)) changes.add("继承角色从 " + previousParents + " 改为 " + parents);
    role.name=input.name; role.description=input.description;
    if (!changes.isEmpty()) logs.recordGlobalChange(actor,"修改角色",String.join("，",changes),"ROLE",role.id,role.name);
    statuses.settle();
    return details(role);
  }

  @Transactional
  public void delete(SessionUser actor, long roleId) {
    requireTotalAdmin(actor);
    var role=requireEditableRole(roleId);
    var count=assignments.countByRoleId(roleId);
    if (count>0) throw new ManagementFailure(CONFLICT,"该角色仍关联 " + count + " 个用户，请先撤销用户关联。");
    if (inheritance.existsByRoleIdOrInheritedRoleId(roleId,roleId))
      throw new ManagementFailure(CONFLICT,"该角色仍存在父角色或子角色，请先解除继承关系。");
    grants.deleteAll(grants.findByRoleIdOrderByPermissionIdAsc(roleId));
    grants.flush(); roles.delete(role);
    logs.recordGlobalChange(actor,"删除角色","删除无用户关联且无继承关系的业务角色","ROLE",role.id,role.name);
  }

  @Transactional(readOnly = true)
  public void validateNew(RoleInput input) {
    var graph=effective.load();
    validateSelection(graph,input,Set.of());
    var ids=new TreeSet<>(input.permissionIds);
    ids.addAll(effective.sources(graph,input.inheritedRoleIds).keySet());
    validateMenus(graph,ids,input.name);
  }

  private void validateSelection(Graph graph, RoleInput input, Set<Long> existingDirect) {
    if (!graph.permissions().keySet().containsAll(input.permissionIds))
      throw new ManagementFailure(INVALID_REQUEST,"选择的权限不存在。");
    for (var id : input.inheritedRoleIds) {
      var parent=graph.roles().get(id);
      if (parent==null || !"BUSINESS".equals(parent.roleType))
        throw new ManagementFailure(INVALID_REQUEST,"父角色必须是当前存在的业务角色。");
    }
    var inherited=effective.sources(graph,input.inheritedRoleIds).keySet();
    for (var id : input.permissionIds)
      if (!existingDirect.contains(id) && inherited.contains(id))
        throw new ManagementFailure(INVALID_REQUEST,"权限「" + graph.permissions().get(id).name + "」已通过继承获得，不能新增重复直接配置。");
  }

  private void validateMenus(Graph graph, Set<Long> ids, String roleName) {
    for (var id : ids) {
      var permission=graph.permissions().get(id);
      if (permission.parentId!=null && !ids.contains(permission.parentId))
        throw new ManagementFailure(INVALID_REQUEST,"角色「" + roleName + "」的按钮「" + permission.name
            + "」缺少所属菜单「" + graph.permissions().get(permission.parentId).name + "」。");
    }
  }

  // RBAC0 申请流程在适配前仍使用直接权限校验。
  void validatePermissionSelection(Set<Long> ids) {
    var graph=effective.load();
    if (!graph.permissions().keySet().containsAll(ids)) throw new ManagementFailure(INVALID_REQUEST,"选择的权限不存在。");
    validateMenus(graph,ids,"申请角色");
  }

  private Role requireEditableRole(long id) {
    var role=roles.findBusinessRoleForUpdate(id).orElseThrow(() -> new ManagementFailure(NOT_FOUND,"业务角色不存在。"));
    return role;
  }

  private void savePermissions(long roleId, List<Long> ids) {
    grants.saveAll(ids.stream().map(id -> { var link=new RolePermission(); link.roleId=roleId; link.permissionId=id; return link; }).toList());
  }

  private void saveParents(long roleId, List<Long> ids) {
    inheritance.saveAll(ids.stream().map(id -> { var link=new RoleInheritance(); link.roleId=roleId; link.inheritedRoleId=id; return link; }).toList());
  }

  private RoleDetails details(Role role) {
    return effective.details(effective.load(),role,assignments.countByRoleId(role.id));
  }
}
