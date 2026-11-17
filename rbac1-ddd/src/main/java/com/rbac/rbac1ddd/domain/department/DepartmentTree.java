package com.rbac.rbac1ddd.domain.department;

import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.INVALID_REQUEST;
import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.NOT_FOUND;

import com.rbac.rbac1ddd.domain.shared.DomainException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 部门树：全部部门及其上下级关系的只读快照（领域服务）。
 *
 * <p>构造时把"部门 → 上级"反转为"部门 → 直接下级"，之后只做只读查询：判断部门管理员的管理范围、
 * 组装部门树视图、检查移动部门时是否会形成循环。快照中的部门对象只用于读取名称和上级，
 * 修改部门请通过仓储读取部门聚合。
 */
public final class DepartmentTree {
  private final Map<Long, Department> departments;
  private final Map<Long, Set<Long>> children;
  private final Set<Long> roots;

  /**
   * 根据全部部门构造部门树。
   *
   * @param departments 全部部门，顶级部门的上级为 null
   */
  public DepartmentTree(Collection<Department> departments) {
    TreeMap<Long, Department> byId = new TreeMap<>();
    HashMap<Long, Set<Long>> reversed = new HashMap<>();
    TreeSet<Long> topLevel = new TreeSet<>();
    for (Department department : departments) {
      byId.put(department.getId(), department);
      if (department.getParentId() == null) {
        topLevel.add(department.getId());
      } else {
        reversed.computeIfAbsent(department.getParentId(), key -> new TreeSet<>()).add(department.getId());
      }
    }
    this.departments = Collections.unmodifiableMap(byId);
    this.children = Map.copyOf(reversed);
    this.roots = Collections.unmodifiableSet(topLevel);
  }

  /**
   * 判断部门是否存在。
   *
   * @param departmentId 部门 ID
   * @return 部门在树中时为 true
   */
  public boolean contains(long departmentId) {
    return departments.containsKey(departmentId);
  }

  /**
   * 按 ID 查找部门快照。
   *
   * @param departmentId 部门 ID
   * @return 部门不存在时为空
   */
  public Optional<Department> find(long departmentId) {
    return Optional.ofNullable(departments.get(departmentId));
  }

  /**
   * 全部部门 ID，即总管理员可以管理的组织范围。
   *
   * @return 按 ID 升序排列的部门 ID
   */
  public Set<Long> allIds() {
    return departments.keySet();
  }

  /**
   * 顶级部门（没有上级的部门）。
   *
   * @return 按 ID 升序排列的部门 ID
   */
  public Set<Long> rootIds() {
    return roots;
  }

  /**
   * 部门的直接下级。
   *
   * @param departmentId 部门 ID
   * @return 按 ID 升序排列的直接下级部门 ID，没有下级时为空
   */
  public Set<Long> childrenOf(long departmentId) {
    return Collections.unmodifiableSet(children.getOrDefault(departmentId, Set.of()));
  }

  /**
   * 部门自身及全部下级部门（多层），即部门管理员可以管理的组织范围。
   *
   * <p>按广度优先遍历，已访问的部门不再展开，避免脏数据形成环时死循环。
   *
   * @param rootId 起始部门 ID
   * @return 按 ID 升序排列的部门 ID 集合，包含起始部门
   */
  public Set<Long> subtreeOf(long rootId) {
    TreeSet<Long> visited = new TreeSet<>();
    ArrayDeque<Long> pending = new ArrayDeque<>();
    pending.add(rootId);
    while (!pending.isEmpty()) {
      Long id = pending.removeFirst();
      if (visited.add(id)) pending.addAll(children.getOrDefault(id, Set.of()));
    }
    return visited;
  }

  /**
   * 检查部门能否移动到新的上级下：上级必须存在，且不能是本部门或其下级部门，否则会形成循环。
   *
   * @param departmentId 要移动的部门 ID
   * @param newParentId 新的上级部门 ID，null 表示改为顶级部门（总是允许）
   * @throws DomainException 上级不存在时为 NOT_FOUND，形成循环时为 INVALID_REQUEST
   */
  public void ensureCanMove(long departmentId, Long newParentId) {
    if (newParentId == null) return;
    if (!contains(newParentId)) throw new DomainException(NOT_FOUND, "上级部门不存在。");
    if (subtreeOf(departmentId).contains(newParentId))
      throw new DomainException(INVALID_REQUEST, "上级部门不能是本部门或其下级部门。");
  }
}
