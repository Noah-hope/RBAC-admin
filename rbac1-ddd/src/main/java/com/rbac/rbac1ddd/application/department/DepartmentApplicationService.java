package com.rbac.rbac1ddd.application.department;

import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.INVALID_REQUEST;
import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.NOT_FOUND;

import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.application.shared.OperationRecorder;
import com.rbac.rbac1ddd.application.shared.OperationTarget;
import com.rbac.rbac1ddd.domain.department.Department;
import com.rbac.rbac1ddd.domain.department.DepartmentRepository;
import com.rbac.rbac1ddd.domain.department.DepartmentTree;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用服务：部门树查询、新增和修改部门。
 *
 * <p>只负责编排：检查操作者身份、读取部门、调用部门聚合和部门树的业务方法、保存、写全局操作记录。
 * 防止循环的规则在 {@link DepartmentTree}，改名和调整上级在 {@link Department}。
 */
@Service
@RequiredArgsConstructor
public class DepartmentApplicationService {
  /** 操作记录中顶级部门的上级名称。 */
  private static final String TOP_LEVEL = "顶层";

  private final DepartmentRepository departments;
  private final OperationRecorder recorder;

  /**
   * 按管理范围返回部门树：总管理员从全部顶级部门开始，部门管理员从本部门开始。
   *
   * @return 根节点列表，各层下级按 ID 升序
   */
  @Transactional(readOnly = true)
  public List<DepartmentNodeResult> tree(Operator operator) {
    DepartmentTree tree = departments.loadTree();
    operator.manageableDepartments(tree, null);
    Collection<Long> roots = operator.isTotalAdmin() ? tree.rootIds() : Set.of(operator.departmentId());
    return roots.stream().map(id -> node(tree, id)).toList();
  }

  /** 总管理员新增部门，上级必须存在，写入全局操作记录。 */
  @Transactional
  public DepartmentResult create(Operator operator, String name, Long parentId) {
    operator.requireTotalAdmin();
    Department parent = parentId == null ? null : require(parentId);
    Department department = Department.create(name, parentId);
    departments.save(department);
    recorder.recordGlobal(
        operator, "新增部门", "上级为" + label(parent), OperationTarget.department(department));
    return DepartmentResult.of(department);
  }

  /**
   * 总管理员修改部门名称或上级部门；有实际变化才写操作记录。
   *
   * <p>上级不能是本部门或其下级部门，失败时整次修改回滚。
   */
  @Transactional
  public DepartmentResult update(Operator operator, long departmentId, DepartmentUpdateCommand command) {
    operator.requireTotalAdmin();
    if (command.isEmpty()) throw new DomainException(INVALID_REQUEST, "请填写部门名称或上级部门。");
    Department department = require(departmentId);
    List<String> changes = new ArrayList<>();
    String oldName = department.getName();
    if (command.name() != null && department.rename(command.name()))
      changes.add("名称从「" + oldName + "」改为「" + command.name() + "」");
    if (command.parentProvided()) {
      DepartmentTree tree = departments.loadTree();
      Long oldParentId = department.getParentId();
      if (department.moveUnder(command.parentId(), tree))
        changes.add("上级从" + label(tree, oldParentId) + "改为" + label(tree, command.parentId()));
    }
    departments.save(department);
    if (!changes.isEmpty())
      recorder.recordGlobal(
          operator, "修改部门", String.join("，", changes), OperationTarget.department(department));
    return DepartmentResult.of(department);
  }

  /** 读取部门，不存在时拒绝。 */
  private Department require(long departmentId) {
    return departments
        .findById(departmentId)
        .orElseThrow(() -> new DomainException(NOT_FOUND, "部门不存在。"));
  }

  /** 递归组装部门节点，下级按 ID 升序。 */
  private static DepartmentNodeResult node(DepartmentTree tree, long departmentId) {
    Department department = tree.find(departmentId).orElseThrow();
    return new DepartmentNodeResult(
        department.getId(),
        department.getName(),
        department.getParentId(),
        tree.childrenOf(departmentId).stream().map(child -> node(tree, child)).toList());
  }

  /** 操作记录中的上级部门名称：顶级部门为「顶层」，其他部门加书名号。 */
  private static String label(Department parent) {
    return parent == null ? TOP_LEVEL : "「" + parent.getName() + "」";
  }

  /** 按 ID 在部门树中取上级部门名称。 */
  private static String label(DepartmentTree tree, Long parentId) {
    return label(parentId == null ? null : tree.find(parentId).orElse(null));
  }
}
