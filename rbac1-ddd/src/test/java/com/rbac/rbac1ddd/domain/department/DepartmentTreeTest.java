package com.rbac.rbac1ddd.domain.department;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.shared.DomainException.Reason;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 部门树单元测试：不启动 Spring，验证上下级查询和移动部门时的防环规则（沿用 RBAC0 规则）。 */
class DepartmentTreeTest {

  // 1 总部 → 2 研发部 → 3 前端组；1 → 4 财务部；5 分公司是另一个顶级部门
  private final DepartmentTree tree =
      new DepartmentTree(
          List.of(
              new Department(4L, "财务部", 1L),
              new Department(3L, "前端组", 2L),
              new Department(5L, "分公司", null),
              new Department(2L, "研发部", 1L),
              new Department(1L, "总部", null)));

  @Test
  @DisplayName("RBAC0：部门树按 ID 升序给出顶级部门、直接下级和整棵子树")
  void 部门树按ID升序给出顶级部门直接下级和子树() {
    assertThat(tree.rootIds()).containsExactly(1L, 5L);
    assertThat(tree.childrenOf(1L)).containsExactly(2L, 4L);
    assertThat(tree.childrenOf(3L)).isEmpty();
    assertThat(tree.subtreeOf(2L)).containsExactly(2L, 3L);
    assertThat(tree.allIds()).containsExactly(1L, 2L, 3L, 4L, 5L);
    assertThat(tree.find(3L)).get().extracting(Department::getName).isEqualTo("前端组");
    assertThat(tree.find(99L)).isEmpty();
  }

  @Test
  @DisplayName("RBAC0：上级部门不能是本部门或其下级部门")
  void 上级部门不能是本部门或其下级部门() {
    assertThatThrownBy(() -> tree.ensureCanMove(2L, 3L))
        .isInstanceOf(DomainException.class)
        .hasMessage("上级部门不能是本部门或其下级部门。")
        .extracting(error -> ((DomainException) error).reason())
        .isEqualTo(Reason.INVALID_REQUEST);
    assertThatThrownBy(() -> tree.ensureCanMove(2L, 2L))
        .hasMessage("上级部门不能是本部门或其下级部门。");
    assertThatCode(() -> tree.ensureCanMove(3L, 4L)).doesNotThrowAnyException();
    assertThatCode(() -> tree.ensureCanMove(2L, null)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("RBAC0：移动到不存在的上级部门时拒绝")
  void 移动到不存在的上级部门时拒绝() {
    assertThatThrownBy(() -> tree.ensureCanMove(2L, 99L))
        .isInstanceOf(DomainException.class)
        .hasMessage("上级部门不存在。")
        .extracting(error -> ((DomainException) error).reason())
        .isEqualTo(Reason.NOT_FOUND);
  }
}
