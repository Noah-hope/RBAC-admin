package com.rbac.rbac1.service;

import com.rbac.rbac1.entity.BusinessApplication;
import com.rbac.rbac1.entity.Permission;
import com.rbac.rbac1.entity.Role;
import com.rbac.rbac1.entity.RoleInheritance;
import com.rbac.rbac1.entity.RolePermission;
import com.rbac.rbac1.entity.UserAccount;
import com.rbac.rbac1.mapper.ApplicationMapper;
import com.rbac.rbac1.mapper.PermissionMapper;
import com.rbac.rbac1.mapper.RoleInheritanceMapper;
import com.rbac.rbac1.mapper.RoleMapper;
import com.rbac.rbac1.mapper.RolePermissionMapper;
import com.rbac.rbac1.mapper.UserMapper;
import com.rbac.rbac1.mapper.UserRoleMapper;
import com.rbac.rbac1.vo.EffectivePermissionVO;
import com.rbac.rbac1.vo.RoleApplicationSummaryVO;
import com.rbac.rbac1.vo.RoleDetailsVO;
import com.rbac.rbac1.vo.RoleRefVO;
import com.rbac.rbac1.vo.RoleSummaryVO;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色继承与有效权限计算：一次操作加载一份关系图，在内存中沿继承链求有效权限和权限来源。
 *
 * <p>不缓存跨请求的授权结果，保证授权变化后下一次请求立即生效。
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RolePermissionService {

  private final RoleMapper roles;

  private final RolePermissionMapper grants;

  private final RoleInheritanceMapper inheritance;

  private final PermissionMapper permissions;

  private final ApplicationMapper applications;

  private final UserMapper users;

  private final UserRoleMapper assignments;

  /**
   * 某一时刻的角色、权限和继承关系快照。
   *
   * @param roles 角色，按 ID 索引
   * @param permissions 权限，按 ID 索引
   * @param applications 应用，按 ID 索引
   * @param direct 角色 ID 到直接授予的权限 ID
   * @param parents 角色 ID 到它直接继承的角色 ID
   */
  public record Graph(
      Map<Long, Role> roles,
      Map<Long, Permission> permissions,
      Map<Long, BusinessApplication> applications,
      Map<Long, Set<Long>> direct,
      Map<Long, Set<Long>> parents) {}

  /**
   * 加载当前完整的关系图。
   *
   * @return 关系快照，直接权限和继承关系内部按 ID 升序
   */
  public Graph load() {
    Map<Long, Set<Long>> direct = new HashMap<>();
    for (RolePermission grant : grants.findAll()) {
      direct.computeIfAbsent(grant.getRoleId(), id -> new TreeSet<>()).add(grant.getPermissionId());
    }
    Map<Long, Set<Long>> parents = new HashMap<>();
    for (RoleInheritance link : inheritance.findAll()) {
      parents
          .computeIfAbsent(link.getRoleId(), id -> new TreeSet<>())
          .add(link.getInheritedRoleId());
    }
    return new Graph(
        roles.findAll().stream().collect(Collectors.toMap(r -> r.getId(), r -> r)),
        permissions.findAll().stream().collect(Collectors.toMap(p -> p.getId(), p -> p)),
        applications.findAll().stream().collect(Collectors.toMap(a -> a.getId(), a -> a)),
        direct,
        parents);
  }

  /**
   * 一个用户的有效授权。
   *
   * @param graph 计算所用的关系图
   * @param roleIds 用户直接分配的角色 ID，升序；停用用户为空
   * @param sources 有效权限 ID 到提供该权限的角色 ID（沿继承链直接授予它的角色）
   */
  public record UserPermissions(Graph graph, List<Long> roleIds, Map<Long, Set<Long>> sources) {}

  /**
   * 计算单个用户的有效授权。
   *
   * @param userId 用户 ID
   * @return 用户的有效授权
   */
  public UserPermissions forUser(long userId) {
    return forUsers(load(), List.of(userId)).get(userId);
  }

  /**
   * 批量计算多个用户的有效授权。停用的用户视为没有任何角色。
   *
   * @param graph 关系图
   * @param userIds 用户 ID
   * @return 用户 ID 到有效授权，每个传入 ID 都有对应项
   */
  public Map<Long, UserPermissions> forUsers(Graph graph, Collection<Long> userIds) {
    if (userIds.isEmpty()) {
      return Map.of();
    }
    Set<Long> active =
        users.findAllById(userIds).stream()
            .filter(u -> UserAccount.ACTIVE.equals(u.getStatus()))
            .map(u -> u.getId())
            .collect(Collectors.toSet());
    Map<Long, List<Long>> assigned =
        assignments.findByUserIdIn(userIds).stream()
            .collect(
                Collectors.groupingBy(
                    a -> a.getUserId(), Collectors.mapping(a -> a.getRoleId(), Collectors.toList())));
    Map<Long, UserPermissions> result = new HashMap<>();
    for (Long id : userIds) {
      List<Long> ids =
          active.contains(id)
              ? assigned.getOrDefault(id, List.of()).stream().sorted().toList()
              : List.<Long>of();
      result.put(id, new UserPermissions(graph, ids, sources(graph, ids)));
    }
    return result;
  }

  /**
   * 求给定角色及其全部祖先角色（被继承的角色）。
   *
   * @param graph 关系图
   * @param roleIds 起点角色 ID
   * @return 起点角色和全部祖先角色 ID，升序
   */
  public Set<Long> ancestors(Graph graph, Collection<Long> roleIds) {
    TreeSet<Long> visited = new TreeSet<>();
    ArrayDeque<Long> pending = new ArrayDeque<>(roleIds);
    while (!pending.isEmpty()) {
      Long id = pending.removeFirst();
      if (visited.add(id)) {
        pending.addAll(graph.parents.getOrDefault(id, Set.of()));
      }
    }
    return visited;
  }

  /**
   * 求给定角色及其全部后代角色（直接或间接继承它的角色），用于判断循环继承和受影响用户。
   *
   * @param graph 关系图
   * @param roleId 起点角色 ID
   * @return 起点角色和全部后代角色 ID，升序
   */
  public Set<Long> descendants(Graph graph, long roleId) {
    Map<Long, Set<Long>> children = new HashMap<>();
    graph.parents.forEach(
        (child, parents) ->
            parents.forEach(
                parent -> children.computeIfAbsent(parent, id -> new TreeSet<>()).add(child)));
    TreeSet<Long> visited = new TreeSet<>();
    ArrayDeque<Long> pending = new ArrayDeque<>();
    pending.add(roleId);
    while (!pending.isEmpty()) {
      Long id = pending.removeFirst();
      if (visited.add(id)) {
        pending.addAll(children.getOrDefault(id, Set.of()));
      }
    }
    return visited;
  }

  /**
   * 计算一组角色的有效权限及来源角色。
   *
   * @param graph 关系图
   * @param roleIds 角色 ID
   * @return 权限 ID 到直接授予该权限的角色 ID，均升序
   */
  public Map<Long, Set<Long>> sources(Graph graph, Collection<Long> roleIds) {
    TreeMap<Long, Set<Long>> result = new TreeMap<>();
    for (Long roleId : ancestors(graph, roleIds)) {
      for (Long permission : graph.direct.getOrDefault(roleId, Set.of())) {
        result.computeIfAbsent(permission, id -> new TreeSet<>()).add(roleId);
      }
    }
    return result;
  }

  /**
   * 计算单个角色的有效权限 ID（直接授予加继承所得）。
   *
   * @param graph 关系图
   * @param roleId 角色 ID
   * @return 有效权限 ID，升序
   */
  public Set<Long> effectiveIds(Graph graph, long roleId) {
    return sources(graph, List.of(roleId)).keySet();
  }

  /**
   * 把角色 ID 转换为带名称的引用。
   *
   * @param graph 关系图
   * @param ids 角色 ID
   * @return 按 ID 升序的角色引用
   */
  public List<RoleRefVO> references(Graph graph, Collection<Long> ids) {
    return ids.stream()
        .sorted()
        .map(id -> new RoleRefVO(id, graph.roles.get(id).getName()))
        .toList();
  }

  /**
   * 角色直接继承的角色。
   *
   * @param graph 关系图
   * @param roleId 角色 ID
   * @return 父角色引用，升序
   */
  public List<RoleRefVO> parents(Graph graph, long roleId) {
    return references(graph, graph.parents.getOrDefault(roleId, Set.of()));
  }

  /**
   * 直接继承该角色的角色。
   *
   * @param graph 关系图
   * @param roleId 角色 ID
   * @return 子角色引用，升序
   */
  public List<RoleRefVO> children(Graph graph, long roleId) {
    return references(
        graph,
        graph.parents.entrySet().stream()
            .filter(e -> e.getValue().contains(roleId))
            .map(Map.Entry::getKey)
            .toList());
  }

  /**
   * 角色的有效权限明细，标出获得方式：只直接授予为 DIRECT，只来自继承为 INHERITED，两者都有为 BOTH。
   *
   * @param graph 关系图
   * @param roleId 角色 ID
   * @return 按权限 ID 升序的有效权限
   */
  public List<EffectivePermissionVO> effective(Graph graph, long roleId) {
    Set<Long> direct = graph.direct.getOrDefault(roleId, Set.of());
    Map<Long, Set<Long>> inherited = sources(graph, graph.parents.getOrDefault(roleId, Set.of()));
    Map<Long, Set<Long>> all = sources(graph, List.of(roleId));
    return all.entrySet().stream()
        .map(
            entry -> {
              Permission p = graph.permissions.get(entry.getKey());
              String acquisition =
                  direct.contains(p.getId())
                      ? inherited.containsKey(p.getId()) ? "BOTH" : "DIRECT"
                      : "INHERITED";
              return new EffectivePermissionVO(
                  p.getId(),
                  p.getApplicationId(),
                  p.getName(),
                  p.getCode(),
                  p.getType(),
                  p.getParentId(),
                  acquisition,
                  references(graph, entry.getValue()));
            })
        .toList();
  }

  /**
   * 预览尚未保存的角色的有效权限，用于新建角色申请的审核页面。
   *
   * <p>来源角色只列出被继承的角色，因为待建角色本身还没有 ID。
   *
   * @param graph 关系图
   * @param direct 待建角色直接授予的权限 ID
   * @param parents 待建角色继承的角色 ID
   * @return 按权限 ID 升序的有效权限
   */
  public List<EffectivePermissionVO> preview(Graph graph, Set<Long> direct, List<Long> parents) {
    Map<Long, Set<Long>> inherited = sources(graph, parents);
    TreeSet<Long> ids = new TreeSet<>(inherited.keySet());
    ids.addAll(direct);
    return ids.stream()
        .map(
            id -> {
              Permission p = graph.permissions.get(id);
              String acquisition =
                  direct.contains(id) ? inherited.containsKey(id) ? "BOTH" : "DIRECT" : "INHERITED";
              return new EffectivePermissionVO(
                  p.getId(),
                  p.getApplicationId(),
                  p.getName(),
                  p.getCode(),
                  p.getType(),
                  p.getParentId(),
                  acquisition,
                  references(graph, inherited.getOrDefault(id, Set.of())));
            })
        .toList();
  }

  /**
   * 按应用统计权限数量。
   *
   * @param graph 关系图
   * @param permissionIds 权限 ID
   * @return 按应用 ID 升序的统计
   */
  public List<RoleApplicationSummaryVO> applications(Graph graph, Set<Long> permissionIds) {
    TreeMap<Long, Long> counts = new TreeMap<>();
    permissionIds.forEach(
        id -> counts.merge(graph.permissions.get(id).getApplicationId(), 1L, Long::sum));
    return counts.entrySet().stream()
        .map(
            e ->
                new RoleApplicationSummaryVO(
                    e.getKey(), graph.applications.get(e.getKey()).getName(), e.getValue()))
        .toList();
  }

  /**
   * 角色列表中的一行摘要。
   *
   * @param graph 关系图
   * @param role 角色
   * @return 角色摘要，包含继承关系和权限数量
   */
  public RoleSummaryVO summary(Graph graph, Role role) {
    Set<Long> effective = effectiveIds(graph, role.getId());
    return new RoleSummaryVO(
        role.getId(),
        role.getName(),
        role.getDescription(),
        applications(graph, effective),
        role.getRoleType(),
        parents(graph, role.getId()),
        children(graph, role.getId()),
        graph.direct.getOrDefault(role.getId(), Set.of()).size(),
        effective.size());
  }

  /**
   * 角色详情。
   *
   * @param graph 关系图
   * @param role 角色
   * @param assignedUserCount 直接分配该角色的用户数
   * @return 角色详情，包含直接权限和有效权限明细
   */
  public RoleDetailsVO details(Graph graph, Role role, long assignedUserCount) {
    List<EffectivePermissionVO> permissions = effective(graph, role.getId());
    Set<Long> ids = permissions.stream().map(EffectivePermissionVO::id).collect(Collectors.toSet());
    List<Long> direct = graph.direct.getOrDefault(role.getId(), Set.of()).stream().sorted().toList();
    return new RoleDetailsVO(
        role.getId(),
        role.getName(),
        role.getDescription(),
        applications(graph, ids),
        role.getRoleType(),
        parents(graph, role.getId()),
        children(graph, role.getId()),
        direct.size(),
        ids.size(),
        direct,
        permissions,
        assignedUserCount);
  }
}
