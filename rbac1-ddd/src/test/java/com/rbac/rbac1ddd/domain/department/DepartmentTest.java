package com.rbac.rbac1ddd.domain.department;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 部门聚合单元测试：不启动 Spring，验证新增、改名和调整上级部门（沿用 RBAC0 规则）。 */
class DepartmentTest {

  // 1 总部 → 2 研发部 → 3 前端组；1 → 4 财务部
  private final DepartmentTree tree =
      new DepartmentTree(
          List.of(
              new Department(1L, "总部", null),
              new Department(2L, "研发部", 1L),
              new Department(3L, "前端组", 2L),
              new Department(4L, "财务部", 1L)));

  @Test
  @DisplayName("RBAC0：新增部门保存后回填 ID")
  void 新增部门保存后回填ID() {
    Department department = Department.create("测试部", 1L);

    assertThat(department.getId()).isNull();
    department.assignId(9L);

    assertThat(department.getId()).isEqualTo(9L);
    assertThat(department.getName()).isEqualTo("测试部");
    assertThat(department.getParentId()).isEqualTo(1L);
    assertThatThrownBy(() -> department.assignId(10L)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("RBAC0：部门改名和调整上级只在实际变化时返回 true")
  void 部门改名和调整上级只在实际变化时返回true() {
    Department department = new Department(3L, "前端组", 2L);

    assertThat(department.rename("前端组")).isFalse();
    assertThat(department.rename("前端部")).isTrue();
    assertThat(department.getName()).isEqualTo("前端部");
    assertThat(department.moveUnder(2L, tree)).isFalse();
    assertThat(department.moveUnder(4L, tree)).isTrue();
    assertThat(department.getParentId()).isEqualTo(4L);
    assertThat(department.moveUnder(null, tree)).isTrue();
    assertThat(department.getParentId()).isNull();
  }

  @Test
  @DisplayName("RBAC0：移动部门形成循环时拒绝且上级不变")
  void 移动部门形成循环时拒绝且上级不变() {
    Department department = new Department(2L, "研发部", 1L);

    assertThatThrownBy(() -> department.moveUnder(3L, tree))
        .hasMessage("上级部门不能是本部门或其下级部门。");
    assertThat(department.getParentId()).isEqualTo(1L);
  }
}
