package com.rbac.rbac1ddd.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rbac.rbac1ddd.domain.department.Department;
import com.rbac.rbac1ddd.domain.department.DepartmentTree;
import com.rbac.rbac1ddd.domain.user.ManagementRole;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.shared.DomainException.Reason;
import com.rbac.rbac1ddd.domain.user.User;
import com.rbac.rbac1ddd.domain.user.UserStatus;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 操作者单元测试：不启动 Spring，验证管理身份与部门管理范围的判断（沿用 RBAC0 规则）。 */
class OperatorTest {

  // 1 总部 → 2 研发部 → 3 前端组；1 → 4 财务部
  private final DepartmentTree tree =
      new DepartmentTree(
          List.of(
              new Department(1L, "总部", null),
              new Department(2L, "研发部", 1L),
              new Department(3L, "前端组", 2L),
              new Department(4L, "财务部", 1L)));

  /** 创建具有指定管理身份、所属部门的操作者。 */
  private static Operator operator(ManagementRole role, Long departmentId) {
    return new Operator(1, "admin", "管理员", departmentId, role);
  }

  /** 创建属于指定部门的启用用户；部门可以为 null，表示没有所属部门。 */
  private static User userIn(Long departmentId) {
    return new User(9L, "u", "用户", departmentId, "hash", UserStatus.ACTIVE, Set.of());
  }

  @Test
  @DisplayName("RBAC0：部门管理员只能管理本部门及下级部门的用户")
  void 部门管理员管理本部门及下级部门() {
    ManagementScope scope = operator(ManagementRole.DEPARTMENT_ADMIN, 2L).scopeIn(tree);

    assertThat(scope.covers(userIn(2L))).isTrue();
    assertThat(scope.covers(userIn(3L))).isTrue();
    assertThat(scope.covers(userIn(4L))).isFalse();
    assertThat(scope.covers(userIn(null))).isFalse();
    assertThatThrownBy(() -> scope.ensureCovers(List.of(userIn(3L), userIn(4L))))
        .isInstanceOf(DomainException.class)
        .hasMessage("请选择本部门或下级部门内的用户。")
        .extracting(e -> ((DomainException) e).reason())
        .isEqualTo(Reason.OUTSIDE_MANAGEMENT_SCOPE);
  }

  @Test
  @DisplayName("RBAC0：总管理员不受部门限制")
  void 总管理员不受部门限制() {
    ManagementScope scope = operator(ManagementRole.TOTAL_ADMIN, null).scopeIn(tree);
    scope.ensureCovers(List.of(userIn(4L), userIn(null)));
  }

  @Test
  @DisplayName("RBAC0：没有所属部门的部门管理员不能管理用户")
  void 没有所属部门的部门管理员不能管理用户() {
    assertThatThrownBy(() -> operator(ManagementRole.DEPARTMENT_ADMIN, null).scopeIn(tree))
        .hasMessage("当前部门管理员没有所属部门。");
  }

  @Test
  @DisplayName("RBAC0：普通用户不能执行管理操作，部门管理员不能执行总管理员操作")
  void 普通用户不能执行管理操作() {
    Operator operator = operator(ManagementRole.NONE, 2L);
    assertThatThrownBy(operator::requireAdministrator).hasMessage("当前角色无权管理用户和部门。");
    assertThatThrownBy(() -> operator.scopeIn(tree)).hasMessage("当前角色无权管理用户和部门。");
    assertThatThrownBy(operator(ManagementRole.DEPARTMENT_ADMIN, 2L)::requireTotalAdmin)
        .hasMessage("当前操作仅限总管理员。");
  }

  @Test
  @DisplayName("RBAC0：部门管理员只能选择本部门或下级部门，不存在的部门先报不存在")
  void 部门管理员只能选择本部门或下级部门() {
    Operator manager = operator(ManagementRole.DEPARTMENT_ADMIN, 2L);

    assertThat(manager.requireManagedDepartment(tree, 3L).getName()).isEqualTo("前端组");
    assertThatThrownBy(() -> manager.requireManagedDepartment(tree, 4L))
        .isInstanceOf(DomainException.class)
        .hasMessage("请选择本部门或下级部门内的对象。")
        .extracting(e -> ((DomainException) e).reason())
        .isEqualTo(Reason.OUTSIDE_MANAGEMENT_SCOPE);
    assertThatThrownBy(() -> manager.requireManagedDepartment(tree, 99L))
        .hasMessage("部门不存在。")
        .extracting(e -> ((DomainException) e).reason())
        .isEqualTo(Reason.NOT_FOUND);
    assertThatThrownBy(
            () -> operator(ManagementRole.DEPARTMENT_ADMIN, null).requireManagedDepartment(tree, 99L))
        .hasMessage("部门不存在。");
    assertThat(operator(ManagementRole.TOTAL_ADMIN, null).requireManagedDepartment(tree, 4L).getId())
        .isEqualTo(4L);
  }

  @Test
  @DisplayName("RBAC0：可管理部门为所选部门子树，未选择时部门管理员为本部门子树、总管理员为全部部门")
  void 可管理部门为所选部门子树() {
    Operator manager = operator(ManagementRole.DEPARTMENT_ADMIN, 2L);
    Operator total = operator(ManagementRole.TOTAL_ADMIN, null);

    assertThat(manager.manageableDepartments(tree, null)).containsExactly(2L, 3L);
    assertThat(manager.manageableDepartments(tree, 3L)).containsExactly(3L);
    assertThat(total.manageableDepartments(tree, null)).containsExactly(1L, 2L, 3L, 4L);
    assertThat(total.manageableDepartments(tree, 2L)).containsExactly(2L, 3L);
    assertThatThrownBy(() -> operator(ManagementRole.NONE, 2L).manageableDepartments(tree, null))
        .hasMessage("当前角色无权管理用户和部门。");
  }

  @Test
  @DisplayName("RBAC0：只有总管理员可以调动用户或任免部门管理员")
  void 只有总管理员可以调动用户或任免部门管理员() {
    operator(ManagementRole.TOTAL_ADMIN, null).requireOrganizationAuthority();
    assertThatThrownBy(operator(ManagementRole.DEPARTMENT_ADMIN, 2L)::requireOrganizationAuthority)
        .isInstanceOf(DomainException.class)
        .hasMessage("只有总管理员可以调动用户或任免部门管理员。")
        .extracting(e -> ((DomainException) e).reason())
        .isEqualTo(Reason.FORBIDDEN);
  }
}
