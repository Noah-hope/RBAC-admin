package com.rbac.rbac1ddd.application.auth;

import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.OUTSIDE_MANAGEMENT_SCOPE;

import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.user.User;
import java.util.Collection;
import java.util.Set;

/** 操作者可以管理的部门范围，属于应用层权限守卫。总管理员不受限制。 */
public final class ManagementScope {
  /** 可管理的部门 ID；为 null 表示不受限制。 */
  private final Set<Long> departmentIds;

  private ManagementScope(Set<Long> departmentIds) {
    this.departmentIds = departmentIds;
  }

  /** 不限部门的管理范围，用于总管理员。 */
  public static ManagementScope unlimited() {
    return new ManagementScope(null);
  }

  /**
   * 限定在指定部门集合内的管理范围。
   *
   * @param departmentIds 本部门及全部下级部门的 ID
   */
  public static ManagementScope of(Set<Long> departmentIds) {
    return new ManagementScope(Set.copyOf(departmentIds));
  }

  /** 用户是否在管理范围内；受限范围下，没有部门的用户不在范围内。 */
  public boolean covers(User user) {
    return departmentIds == null
        || (user.getDepartmentId() != null && departmentIds.contains(user.getDepartmentId()));
  }

  /** 要求全部用户都在管理范围内，任一用户超出范围即整体拒绝。 */
  public void ensureCovers(Collection<User> users) {
    if (!users.stream().allMatch(this::covers))
      throw new DomainException(OUTSIDE_MANAGEMENT_SCOPE, "请选择本部门或下级部门内的用户。");
  }

  /** 要求部门在管理范围内，用于按部门筛选或操作某个部门内的对象。 */
  public void ensureCoversDepartment(long departmentId) {
    if (departmentIds != null && !departmentIds.contains(departmentId))
      throw new DomainException(OUTSIDE_MANAGEMENT_SCOPE, "请选择本部门或下级部门内的对象。");
  }
}
