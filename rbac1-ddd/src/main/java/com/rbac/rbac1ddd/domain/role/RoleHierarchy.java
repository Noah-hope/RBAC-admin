package com.rbac.rbac1ddd.domain.role;

import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.INVALID_REQUEST;
import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.NOT_FOUND;

import com.rbac.rbac1ddd.domain.shared.DomainException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 领域服务：角色继承图（有向无环图）。
 *
 * <p>单个 {@link Role} 只知道自己的父角色，判断"是否成环"、"有哪些后代"需要看整张图，
 * 因此按 GRASP 纯虚构原则单独建模。对象不可变：修改前用 {@link #withConfiguration} 生成假设图做校验，
 * 校验失败时原图不受影响。
 */
public final class RoleHierarchy {

  /**
   * 图中的一个角色节点，只保留继承计算需要的数据，与 {@link Role} 聚合解耦。
   *
   * @param name 角色名称，用于错误提示
   * @param type 角色类型
   * @param direct 直接权限 ID
   * @param parents 直接父角色 ID
   */
  private record Node(String name, RoleType type, Set<Long> direct, Set<Long> parents) {}

  private final Map<Long, Node> nodes;
  private final Map<Long, Set<Long>> children;

  /**
   * 复制节点并预先计算"父角色 → 直接子角色"索引，供查找后代使用。
   *
   * @param nodes 角色 ID → 节点
   */
  private RoleHierarchy(Map<Long, Node> nodes) {
    this.nodes = Map.copyOf(nodes);
    HashMap<Long, Set<Long>> reversed = new HashMap<>();
    nodes.forEach(
        (child, node) ->
            node.parents().forEach(p -> reversed.computeIfAbsent(p, id -> new TreeSet<>()).add(child)));
    this.children = Map.copyOf(reversed);
  }

  /**
   * 用全部角色构造继承图。
   *
   * @param roles 系统中的全部角色，必须都已有 ID
   * @return 不可变的角色继承图
   */
  public static RoleHierarchy of(Collection<Role> roles) {
    HashMap<Long, Node> nodes = new HashMap<>();
    for (Role role : roles) {
      nodes.put(
          role.getId(),
          new Node(
              role.getName(),
              role.getType(),
              Set.copyOf(role.getDirectPermissionIds()),
              Set.copyOf(role.getParentRoleIds())));
    }
    return new RoleHierarchy(nodes);
  }

  /**
   * 返回把某个角色换成新配置后的假设继承图，用于保存前校验。
   *
   * @param roleId 被修改的角色 ID，必须已在图中
   * @param direct 新的直接权限 ID
   * @param parents 新的父角色 ID
   * @return 新的继承图，当前图保持不变
   */
  public RoleHierarchy withConfiguration(long roleId, Set<Long> direct, Set<Long> parents) {
    HashMap<Long, Node> copy = new HashMap<>(nodes);
    Node old = copy.get(roleId);
    copy.put(roleId, new Node(old.name(), old.type(), Set.copyOf(direct), Set.copyOf(parents)));
    return new RoleHierarchy(copy);
  }

  /**
   * 判断角色存在且是业务角色。不存在的角色返回 false，而不是抛异常，便于调用方给出具体提示。
   *
   * @param roleId 角色 ID
   * @return 存在且为业务角色时为 true
   */
  public boolean isBusinessRole(long roleId) {
    Node node = nodes.get(roleId);
    return node != null && node.type() == RoleType.BUSINESS;
  }

  /**
   * 判断角色当前是否存在（不限类型），用于标出申请中保存的父角色快照是否已被删除。
   *
   * @param roleId 角色 ID
   * @return 角色存在时为 true
   */
  public boolean contains(long roleId) {
    return nodes.containsKey(roleId);
  }

  /**
   * 全部业务角色，用于挑选审批权限申请的候选角色。
   *
   * @return 按 ID 升序排列的业务角色 ID
   */
  public Set<Long> businessRoleIds() {
    TreeSet<Long> result = new TreeSet<>();
    nodes.forEach(
        (id, node) -> {
          if (node.type() == RoleType.BUSINESS) result.add(id);
        });
    return result;
  }

  /**
   * 查询角色类型，用于判断用户的管理身份。
   *
   * @param roleId 已存在的角色 ID
   * @return 角色类型
   */
  public RoleType typeOf(long roleId) {
    return nodes.get(roleId).type();
  }

  /**
   * 校验所选角色都存在且都是业务角色，用于分配或撤销用户角色。
   *
   * @param roleIds 所选角色 ID
   * @return 去重并按 ID 排序后的角色 ID
   * @throws DomainException 有角色不存在时为 NOT_FOUND，含预置管理角色时为 INVALID_REQUEST
   */
  public Set<Long> requireBusinessRoles(Collection<Long> roleIds) {
    if (!nodes.keySet().containsAll(roleIds))
      throw new DomainException(NOT_FOUND, "所选角色中有角色不存在。");
    if (roleIds.stream().anyMatch(id -> !isBusinessRole(id)))
      throw new DomainException(INVALID_REQUEST, "这里只能分配或撤销业务角色。");
    return new TreeSet<>(roleIds);
  }

  /**
   * 查询角色名称，用于错误提示。
   *
   * @param roleId 已存在的角色 ID
   * @return 角色名称
   */
  public String nameOf(long roleId) {
    return nodes.get(roleId).name();
  }

  /**
   * 角色的直接父角色。
   *
   * @param roleId 已存在的角色 ID
   * @return 新建的可修改集合，按 ID 排序
   */
  public Set<Long> parentsOf(long roleId) {
    return new TreeSet<>(nodes.get(roleId).parents());
  }

  /**
   * 角色的直接子角色。
   *
   * @param roleId 角色 ID
   * @return 新建的可修改集合，按 ID 排序；没有子角色时为空
   */
  public Set<Long> childrenOf(long roleId) {
    return new TreeSet<>(children.getOrDefault(roleId, Set.of()));
  }

  /**
   * 角色有父角色或子角色，即仍参与继承关系。
   *
   * @param roleId 已存在的角色 ID
   * @return 参与继承时为 true
   */
  public boolean participatesInInheritance(long roleId) {
    return !parentsOf(roleId).isEmpty() || !childrenOf(roleId).isEmpty();
  }

  /**
   * 给定角色及其全部可达父角色（多层、多继承）。
   *
   * <p>广度优先遍历，已访问的角色不再展开，因此多继承的公共祖先只计算一次。
   *
   * @param roleIds 起始角色 ID
   * @return 起始角色及全部祖先，按 ID 排序
   */
  public Set<Long> ancestorsIncluding(Collection<Long> roleIds) {
    TreeSet<Long> visited = new TreeSet<>();
    ArrayDeque<Long> pending = new ArrayDeque<>(roleIds);
    while (!pending.isEmpty()) {
      Long id = pending.removeFirst();
      if (visited.add(id)) pending.addAll(nodes.get(id).parents());
    }
    return visited;
  }

  /**
   * 角色自身及全部后代，即修改该角色时受影响的角色。
   *
   * @param roleId 起始角色 ID
   * @return 起始角色及全部后代，按 ID 排序
   */
  public Set<Long> descendantsIncluding(long roleId) {
    TreeSet<Long> visited = new TreeSet<>();
    ArrayDeque<Long> pending = new ArrayDeque<>();
    pending.add(roleId);
    while (!pending.isEmpty()) {
      Long id = pending.removeFirst();
      if (visited.add(id)) pending.addAll(children.getOrDefault(id, Set.of()));
    }
    return visited;
  }

  /**
   * 权限 ID → 直接拥有该权限的来源角色。重复权限只出现一次。
   *
   * @param roleIds 起始角色 ID，来源范围包括它们自身及全部祖先
   * @return 按权限 ID 排序的映射
   */
  public Map<Long, Set<Long>> permissionSources(Collection<Long> roleIds) {
    TreeMap<Long, Set<Long>> result = new TreeMap<>();
    for (Long roleId : ancestorsIncluding(roleIds)) {
      for (Long permission : nodes.get(roleId).direct()) {
        result.computeIfAbsent(permission, id -> new TreeSet<>()).add(roleId);
      }
    }
    return result;
  }

  /**
   * 角色的有效权限 ID（直接权限 ∪ 所有可达父角色的权限）。
   *
   * @param roleId 已存在的角色 ID
   * @return 按权限 ID 排序的集合
   */
  public Set<Long> effectivePermissionIds(long roleId) {
    return permissionSources(List.of(roleId)).keySet();
  }

  /**
   * 角色有效权限 = 直接权限 ∪ 所有可达父角色的权限，并标出获得方式和来源。
   *
   * <p>继承部分按全部父角色链（不含自身）计算；既是直接权限又能从父角色链获得时为 BOTH。
   *
   * @param roleId 已存在的角色 ID
   * @return 按权限 ID 排序的有效权限
   */
  public List<EffectivePermission> effectivePermissionsOf(long roleId) {
    Set<Long> direct = nodes.get(roleId).direct();
    Map<Long, Set<Long>> inherited = permissionSources(nodes.get(roleId).parents());
    return permissionSources(List.of(roleId)).entrySet().stream()
        .map(
            entry -> {
              Long id = entry.getKey();
              Acquisition acquisition =
                  direct.contains(id)
                      ? inherited.containsKey(id) ? Acquisition.BOTH : Acquisition.DIRECT
                      : Acquisition.INHERITED;
              return new EffectivePermission(id, acquisition, entry.getValue());
            })
        .toList();
  }

  /**
   * 预览尚未创建的角色的有效权限：直接权限 ∪ 所选父角色链的权限。
   *
   * <p>用于角色创建申请，按父角色当前配置计算。新角色尚不存在，来源角色只列出提供继承权限的祖先角色，
   * 只有直接配置的权限没有来源角色。调用方须先确认父角色都存在。
   *
   * @param direct 直接权限 ID
   * @param parents 所选父角色 ID，必须都在图中
   * @return 按权限 ID 排序的有效权限
   */
  public List<EffectivePermission> preview(Set<Long> direct, Collection<Long> parents) {
    Map<Long, Set<Long>> inherited = permissionSources(parents);
    TreeSet<Long> ids = new TreeSet<>(inherited.keySet());
    ids.addAll(direct);
    return ids.stream()
        .map(
            id -> {
              Acquisition acquisition =
                  direct.contains(id)
                      ? inherited.containsKey(id) ? Acquisition.BOTH : Acquisition.DIRECT
                      : Acquisition.INHERITED;
              return new EffectivePermission(id, acquisition, inherited.getOrDefault(id, Set.of()));
            })
        .toList();
  }
}
