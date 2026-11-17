package com.rbac.rbac1ddd.application.auth;

import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.FORBIDDEN;
import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.NOT_FOUND;

import com.rbac.rbac1ddd.domain.department.Department;
import com.rbac.rbac1ddd.domain.department.DepartmentTree;
import com.rbac.rbac1ddd.domain.user.ManagementRole;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import java.util.Set;

/**
 * 应用层权限守卫：当前操作者（已登录的用户）及其管理身份。
 *
 * <p>按代码规范，业务权限判断放在应用层，不放进领域对象。操作者知道自己的管理身份和所属部门，
 * 因此由它判断"能不能做"和"能管哪些部门"（GRASP 信息专家）。
 *
 * @param id 用户 ID
 * @param username 登录名
 * @param name 姓名，写入操作记录
 * @param departmentId 所属部门 ID，可能为空
 * @param managementRole 由有效角色推导出的管理身份
 */
public record Operator(
    long id, String username, String name, Long departmentId, ManagementRole managementRole) {
  /** 是否为总管理员。 */
  public boolean isTotalAdmin() {
    return managementRole == ManagementRole.TOTAL_ADMIN;
  }

  /** 要求操作者是总管理员或部门管理员，否则拒绝。 */
  public void requireAdministrator() {
    if (managementRole == ManagementRole.NONE)
      throw new DomainException(FORBIDDEN, "当前角色无权管理用户和部门。");
  }

  /** 要求操作者是总管理员，否则拒绝。 */
  public void requireTotalAdmin() {
    if (!isTotalAdmin()) throw new DomainException(FORBIDDEN, "当前操作仅限总管理员。");
  }

  /**
   * 计算操作者的管理范围：部门管理员管理本部门及下级部门，总管理员不受限制。
   *
   * <p>部门管理员没有所属部门，或所属部门已不在部门树中时拒绝。
   */
  public ManagementScope scopeIn(DepartmentTree departments) {
    requireAdministrator();
    if (isTotalAdmin()) return ManagementScope.unlimited();
    if (departmentId == null || !departments.contains(departmentId))
      throw new DomainException(FORBIDDEN, "当前部门管理员没有所属部门。");
    return ManagementScope.of(departments.subtreeOf(departmentId));
  }

  /** 调动用户、任免部门管理员只能由总管理员执行，部门管理员即使在管理范围内也拒绝。 */
  public void requireOrganizationAuthority() {
    if (!isTotalAdmin())
      throw new DomainException(FORBIDDEN, "只有总管理员可以调动用户或任免部门管理员。");
  }

  /**
   * 操作者可以管理的部门（与 rbac1-structured 的检查顺序相同）。
   *
   * <p>先要求是管理员，再检查所选部门存在，再计算管理范围并要求所选部门在范围内。
   * 未选择部门时，总管理员为全部部门，部门管理员为本部门及下级部门；选择部门时为该部门及下级部门。
   *
   * @param departments 当前部门树
   * @param selectedId 所选部门 ID，null 表示不按部门筛选
   * @return 按 ID 升序排列的部门 ID
   */
  public Set<Long> manageableDepartments(DepartmentTree departments, Long selectedId) {
    requireAdministrator();
    if (selectedId != null && !departments.contains(selectedId))
      throw new DomainException(NOT_FOUND, "部门不存在。");
    ManagementScope scope = scopeIn(departments);
    if (selectedId == null)
      return isTotalAdmin() ? departments.allIds() : departments.subtreeOf(departmentId);
    scope.ensureCoversDepartment(selectedId);
    return departments.subtreeOf(selectedId);
  }

  /**
   * 要求部门存在且在操作者的管理范围内，用于新增、查询和修改用户时检查所属部门。
   *
   * @param departments 当前部门树
   * @param departmentId 部门 ID
   * @return 部门快照
   */
  public Department requireManagedDepartment(DepartmentTree departments, long departmentId) {
    manageableDepartments(departments, departmentId);
    return departments.find(departmentId).orElseThrow();
  }
}
