package com.rbac.rbac1.service;

import static com.rbac.rbac1.service.ManagementFailure.Reason.FORBIDDEN;
import static com.rbac.rbac1.service.ManagementFailure.Reason.INVALID_REQUEST;
import static com.rbac.rbac1.service.ManagementFailure.Reason.NOT_FOUND;
import static com.rbac.rbac1.service.ManagementFailure.Reason.OUTSIDE_MANAGEMENT_SCOPE;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.rbac.rbac1.dto.DepartmentDTO;
import com.rbac.rbac1.dto.DepartmentUpdateDTO;
import com.rbac.rbac1.entity.Department;
import com.rbac.rbac1.entity.OperationLogTarget;
import com.rbac.rbac1.entity.Role;
import com.rbac.rbac1.mapper.DepartmentMapper;
import com.rbac.rbac1.vo.DepartmentNodeVO;
import com.rbac.rbac1.vo.DepartmentTreeVO;
import com.rbac.rbac1.vo.DepartmentVO;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 部门管理与管理范围判断。
 *
 * <p>总管理员管理全部部门；部门管理员只能管理本部门及其下级部门。管理身份的判断也集中在这里，
 * 供其他 Service 复用。
 */
@Service
@RequiredArgsConstructor
public class DepartmentService {

  private final DepartmentMapper departments;

  private final OperationLogService operationLogs;

  /**
   * 查询操作人可见的部门树：总管理员从顶层部门开始，部门管理员从本部门开始。
   *
   * @param actor 当前操作人，必须是管理员
   * @return 部门树
   */
  @Transactional(readOnly = true)
  public DepartmentTreeVO listTree(SessionUser actor) {
    List<Department> visible = managedDepartments(actor, null);
    Map<Long, List<Department>> children = groupChildren(visible);
    List<Department> roots =
        isTotalAdmin(actor) ? children.getOrDefault(null, List.of()) : List.of(visible.getFirst());
    return new DepartmentTreeVO(
        roots.stream().map(department -> node(department, children)).toList());
  }

  /**
   * 求操作人可管理的部门子树。
   *
   * <p>未选择部门时返回操作人的全部管理范围；选择了部门时，校验它在管理范围内，再返回以它为根的子树。
   * 结果按广度优先排列，第一个元素就是子树的根。
   *
   * @param actor 当前操作人，必须是管理员
   * @param selectedId 选择的部门 ID，可为 {@code null}
   * @return 可管理的部门，第一个为子树根
   * @throws ManagementFailure 部门不存在为 NOT_FOUND，部门管理员没有所属部门为 FORBIDDEN，
   *     超出管理范围为 OUTSIDE_MANAGEMENT_SCOPE
   */
  public List<Department> managedDepartments(SessionUser actor, Long selectedId) {
    requireAdministrator(actor);
    List<Department> all = departments.findAllByOrderByIdAsc();
    boolean totalAdmin = isTotalAdmin(actor);
    if (totalAdmin && selectedId == null) {
      return all;
    }

    Map<Long, Department> byId =
        all.stream().collect(Collectors.toMap(department -> department.getId(), Function.identity()));
    Department root = byId.get(totalAdmin ? selectedId : actor.departmentId());
    Department selected = selectedId == null ? root : byId.get(selectedId);
    if (selectedId != null && selected == null) {
      throw new ManagementFailure(NOT_FOUND, "部门不存在。");
    }
    if (root == null) {
      throw new ManagementFailure(FORBIDDEN, "当前部门管理员没有所属部门。");
    }
    Department ancestor = selected;
    while (ancestor != null && !ancestor.getId().equals(root.getId())) {
      ancestor = byId.get(ancestor.getParentId());
    }
    if (ancestor == null) {
      throw new ManagementFailure(OUTSIDE_MANAGEMENT_SCOPE, "请选择本部门或下级部门内的对象。");
    }

    Map<Long, List<Department>> children = groupChildren(all);
    List<Department> visible = new ArrayList<>();
    ArrayDeque<Department> pending = new ArrayDeque<>();
    pending.add(selected);
    while (!pending.isEmpty()) {
      Department department = pending.removeFirst();
      visible.add(department);
      pending.addAll(children.getOrDefault(department.getId(), List.of()));
    }
    return visible;
  }

  /**
   * 查找操作人可管理的部门。
   *
   * @param actor 当前操作人
   * @param id 部门 ID
   * @return 部门
   * @throws ManagementFailure 部门不存在或超出管理范围
   */
  public Department requireManagedDepartment(SessionUser actor, long id) {
    return isTotalAdmin(actor) ? requireDepartment(id) : managedDepartments(actor, id).getFirst();
  }

  /**
   * 新增部门。
   *
   * @param actor 当前操作人，必须是总管理员
   * @param request 部门名称和可选的上级部门
   * @return 新增的部门
   * @throws ManagementFailure 上级部门不存在时为 NOT_FOUND
   */
  @Transactional
  public DepartmentVO create(SessionUser actor, DepartmentDTO request) {
    requireTotalAdmin(actor);
    Department parent =
        request.getParentId() == null ? null : requireDepartment(request.getParentId());

    Department department = CloneFactory.copy(new Department(), request);
    departments.save(department);
    operationLogs.recordGlobalChange(
        actor,
        "新增部门",
        "上级为" + parentName(parent),
        OperationLogTarget.DEPARTMENT,
        department.getId(),
        department.getName());
    return new DepartmentVO(department.getId(), department.getName(), department.getParentId());
  }

  /**
   * 修改部门名称或上级部门；上级部门显式传 {@code null} 表示移到顶层。
   *
   * <p>新的上级不能是本部门或其下级部门，否则会形成环。只有实际变化的项写入操作记录。
   *
   * @param actor 当前操作人，必须是总管理员
   * @param id 部门 ID
   * @param request 要修改的字段，至少一项
   * @return 修改后的部门
   * @throws ManagementFailure 部门或上级部门不存在为 NOT_FOUND，没有任何字段或形成环为 INVALID_REQUEST
   */
  @Transactional
  public DepartmentVO update(SessionUser actor, long id, DepartmentUpdateDTO request) {
    requireTotalAdmin(actor);
    if (request.getName() == null && !request.isParentProvided()) {
      throw new ManagementFailure(INVALID_REQUEST, "请填写部门名称或上级部门。");
    }

    Department department = requireDepartment(id);
    List<String> changes = new ArrayList<>();
    if (request.getName() != null && !request.getName().equals(department.getName())) {
      changes.add("名称从「" + department.getName() + "」改为「" + request.getName() + "」");
      department.setName(request.getName());
    }
    if (request.isParentProvided()
        && !Objects.equals(request.getParentId(), department.getParentId())) {
      Map<Long, Department> byId =
          departments.findAllByOrderByIdAsc().stream()
              .collect(Collectors.toMap(item -> item.getId(), Function.identity()));
      Department parent = byId.get(request.getParentId());
      if (request.getParentId() != null && parent == null) {
        throw new ManagementFailure(NOT_FOUND, "上级部门不存在。");
      }
      for (Department ancestor = parent;
          ancestor != null;
          ancestor = byId.get(ancestor.getParentId())) {
        if (ancestor.getId().equals(department.getId())) {
          throw new ManagementFailure(INVALID_REQUEST, "上级部门不能是本部门或其下级部门。");
        }
      }
      changes.add(
          "上级从" + parentName(byId.get(department.getParentId())) + "改为" + parentName(parent));
      department.setParentId(request.getParentId());
    }

    if (!changes.isEmpty()) {
      operationLogs.recordGlobalChange(
          actor,
          "修改部门",
          String.join("，", changes),
          OperationLogTarget.DEPARTMENT,
          department.getId(),
          department.getName());
    }
    return new DepartmentVO(department.getId(), department.getName(), department.getParentId());
  }

  /** 按 ID 查找部门，不存在时抛出 NOT_FOUND。 */
  private Department requireDepartment(long id) {
    return departments
        .findById(id)
        .orElseThrow(() -> new ManagementFailure(NOT_FOUND, "部门不存在。"));
  }

  /**
   * 判断操作人是否为总管理员。
   *
   * @param actor 当前操作人
   * @return 是总管理员时为 {@code true}
   */
  static boolean isTotalAdmin(SessionUser actor) {
    return Role.TOTAL_ADMIN.equals(actor.managementRole());
  }

  /**
   * 要求操作人是总管理员或部门管理员。
   *
   * @param actor 当前操作人
   * @throws ManagementFailure 不是管理员时为 FORBIDDEN
   */
  static void requireAdministrator(SessionUser actor) {
    if (!isTotalAdmin(actor) && !Role.DEPARTMENT_ADMIN.equals(actor.managementRole())) {
      throw new ManagementFailure(FORBIDDEN, "当前角色无权管理用户和部门。");
    }
  }

  /**
   * 要求操作人是总管理员。
   *
   * @param actor 当前操作人
   * @throws ManagementFailure 不是总管理员时为 FORBIDDEN
   */
  static void requireTotalAdmin(SessionUser actor) {
    if (!isTotalAdmin(actor)) {
      throw new ManagementFailure(FORBIDDEN, "当前操作仅限总管理员。");
    }
  }

  /** 按上级部门 ID 分组；顶层部门的键为 {@code null}。 */
  private static Map<Long, List<Department>> groupChildren(List<Department> departments) {
    Map<Long, List<Department>> children = new HashMap<>();
    for (Department department : departments) {
      children.computeIfAbsent(department.getParentId(), key -> new ArrayList<>()).add(department);
    }
    return children;
  }

  /** 递归组装部门树节点。 */
  private static DepartmentNodeVO node(Department department, Map<Long, List<Department>> children) {
    List<DepartmentNodeVO> descendants =
        children.getOrDefault(department.getId(), List.of()).stream()
            .map(child -> node(child, children))
            .toList();
    return new DepartmentNodeVO(
        department.getId(), department.getName(), department.getParentId(), descendants);
  }

  /** 操作记录中显示的上级部门名称，顶层部门显示为「顶层」。 */
  private static String parentName(Department department) {
    return department == null ? "顶层" : "「" + department.getName() + "」";
  }
}
