package com.rbac.rbac1.service;

import com.rbac.rbac1.dto.RoleDtos.*;
import com.rbac.rbac1.dto.UserDtos.RoleRef;
import com.rbac.rbac1.entity.*;
import com.rbac.rbac1.mapper.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 一次操作加载一份关系图；不缓存跨请求的授权结果。 */
@Service
@Transactional(readOnly = true)
public class RolePermissionService {
  private final RoleMapper roles;
  private final RolePermissionMapper grants;
  private final RoleInheritanceMapper inheritance;
  private final PermissionMapper permissions;
  private final ApplicationMapper applications;

  public RolePermissionService(RoleMapper roles, RolePermissionMapper grants,
      RoleInheritanceMapper inheritance, PermissionMapper permissions, ApplicationMapper applications) {
    this.roles=roles; this.grants=grants; this.inheritance=inheritance;
    this.permissions=permissions; this.applications=applications;
  }

  public record Graph(Map<Long, Role> roles, Map<Long, Permission> permissions,
      Map<Long, BusinessApplication> applications, Map<Long, Set<Long>> direct,
      Map<Long, Set<Long>> parents) {}

  public Graph load() {
    var direct=new HashMap<Long, Set<Long>>();
    for (var grant : grants.findAll()) direct.computeIfAbsent(grant.roleId, id -> new TreeSet<>()).add(grant.permissionId);
    var parents=new HashMap<Long, Set<Long>>();
    for (var link : inheritance.findAll()) parents.computeIfAbsent(link.roleId, id -> new TreeSet<>()).add(link.inheritedRoleId);
    return new Graph(roles.findAll().stream().collect(Collectors.toMap(r -> r.id, r -> r)),
        permissions.findAll().stream().collect(Collectors.toMap(p -> p.id, p -> p)),
        applications.findAll().stream().collect(Collectors.toMap(a -> a.id, a -> a)), direct, parents);
  }

  public Set<Long> ancestors(Graph graph, Collection<Long> roleIds) {
    var visited=new TreeSet<Long>(); var pending=new ArrayDeque<>(roleIds);
    while (!pending.isEmpty()) {
      var id=pending.removeFirst();
      if (visited.add(id)) pending.addAll(graph.parents.getOrDefault(id, Set.of()));
    }
    return visited;
  }

  public Set<Long> descendants(Graph graph, long roleId) {
    var children=new HashMap<Long, Set<Long>>();
    graph.parents.forEach((child, parents) -> parents.forEach(parent ->
        children.computeIfAbsent(parent, id -> new TreeSet<>()).add(child)));
    var visited=new TreeSet<Long>(); var pending=new ArrayDeque<Long>(); pending.add(roleId);
    while (!pending.isEmpty()) {
      var id=pending.removeFirst();
      if (visited.add(id)) pending.addAll(children.getOrDefault(id, Set.of()));
    }
    return visited;
  }

  public Map<Long, Set<Long>> sources(Graph graph, Collection<Long> roleIds) {
    var result=new TreeMap<Long, Set<Long>>();
    for (var roleId : ancestors(graph, roleIds))
      for (var permission : graph.direct.getOrDefault(roleId, Set.of()))
        result.computeIfAbsent(permission, id -> new TreeSet<>()).add(roleId);
    return result;
  }

  public Set<Long> effectiveIds(Graph graph, long roleId) {
    return sources(graph, List.of(roleId)).keySet();
  }

  public List<RoleRef> references(Graph graph, Collection<Long> ids) {
    return ids.stream().sorted().map(id -> new RoleRef(id, graph.roles.get(id).name)).toList();
  }

  public List<RoleRef> parents(Graph graph, long roleId) {
    return references(graph, graph.parents.getOrDefault(roleId, Set.of()));
  }

  public List<RoleRef> children(Graph graph, long roleId) {
    return references(graph, graph.parents.entrySet().stream()
        .filter(e -> e.getValue().contains(roleId)).map(Map.Entry::getKey).toList());
  }

  public List<EffectivePermission> effective(Graph graph, long roleId) {
    var direct=graph.direct.getOrDefault(roleId, Set.of());
    var inherited=sources(graph, graph.parents.getOrDefault(roleId, Set.of()));
    var all=sources(graph, List.of(roleId));
    return all.entrySet().stream().map(entry -> {
      var p=graph.permissions.get(entry.getKey());
      var acquisition=direct.contains(p.id) ? inherited.containsKey(p.id) ? "BOTH" : "DIRECT" : "INHERITED";
      return new EffectivePermission(p.id,p.applicationId,p.name,p.code,p.type,p.parentId,
          acquisition,references(graph,entry.getValue()));
    }).toList();
  }

  public List<RoleApplicationSummary> applications(Graph graph, Set<Long> permissionIds) {
    var counts=new TreeMap<Long, Long>();
    permissionIds.forEach(id -> counts.merge(graph.permissions.get(id).applicationId,1L,Long::sum));
    return counts.entrySet().stream().map(e -> new RoleApplicationSummary(e.getKey(),
        graph.applications.get(e.getKey()).name,e.getValue())).toList();
  }

  public RoleSummary summary(Graph graph, Role role) {
    var effective=effectiveIds(graph,role.id);
    return new RoleSummary(role.id,role.name,role.description,applications(graph,effective),role.roleType,
        parents(graph,role.id),children(graph,role.id),graph.direct.getOrDefault(role.id,Set.of()).size(),effective.size());
  }

  public RoleDetails details(Graph graph, Role role, long assignedUserCount) {
    var summary=summary(graph,role);
    return new RoleDetails(role.id,role.name,role.description,summary.applications(),role.roleType,
        summary.inheritedRoles(),summary.inheritedByRoles(),summary.directPermissionCount(),
        summary.effectivePermissionCount(),graph.direct.getOrDefault(role.id,Set.of()).stream().sorted().toList(),
        effective(graph,role.id),assignedUserCount);
  }
}
