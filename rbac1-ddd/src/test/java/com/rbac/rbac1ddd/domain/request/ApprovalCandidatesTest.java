package com.rbac.rbac1ddd.domain.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rbac.rbac1ddd.domain.permission.Permission;
import com.rbac.rbac1ddd.domain.permission.PermissionType;
import com.rbac.rbac1ddd.domain.role.Role;
import com.rbac.rbac1ddd.domain.role.RoleHierarchy;
import com.rbac.rbac1ddd.domain.role.RoleType;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.shared.DomainException.Reason;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 纯领域测试：审批权限申请时的候选角色和无关权限判断（对应结构化版本的 ApprovalRulesTest）。 */
class ApprovalCandidatesTest {

  /** 所申请的按钮 2，属于菜单 1。 */
  static final Permission REQUESTED =
      new Permission(2, 1, 1L, "审核课程", "course:review", PermissionType.BUTTON);

  /** 创建业务角色。 */
  static Role business(long id, Set<Long> direct, Set<Long> parents) {
    return new Role(id, "角色" + id, "", RoleType.BUSINESS, direct, parents);
  }

  @Test
  @DisplayName("验收9：必要的所属菜单和申请人已拥有的权限不算无关权限")
  void 必要菜单和已拥有权限不算无关权限() {
    RoleHierarchy hierarchy =
        RoleHierarchy.of(List.of(business(10, Set.of(1L, 2L, 3L), Set.of())));

    ApprovalCandidates candidates = ApprovalCandidates.evaluate(REQUESTED, Set.of(3L), hierarchy);

    assertThat(candidates.all()).hasSize(1);
    assertThat(candidates.all().get(0).selectable()).isTrue();
    assertThat(candidates.requireSelectable(10).roleId()).isEqualTo(10L);
  }

  @Test
  @DisplayName("验收9：候选角色按全部应用的继承权限评估，附带无关权限时不能选择")
  void 继承的其他应用权限使角色不能选择() {
    // 20 继承 10（菜单 1）并直接配置按钮 2；30 继承 20；40 继承 20 并带有其他应用的权限 4
    RoleHierarchy hierarchy =
        RoleHierarchy.of(
            List.of(
                business(10, Set.of(1L), Set.of()),
                business(20, Set.of(2L), Set.of(10L)),
                business(30, Set.of(), Set.of(20L)),
                business(40, Set.of(4L), Set.of(20L)),
                new Role(1L, "总管理员", "", RoleType.TOTAL_ADMIN, Set.of(1L, 2L), Set.of())));

    ApprovalCandidates candidates = ApprovalCandidates.evaluate(REQUESTED, Set.of(1L), hierarchy);

    assertThat(candidates.all())
        .extracting(ApprovalCandidates.Candidate::roleId)
        .containsExactly(20L, 30L, 40L);
    ApprovalCandidates.Candidate excessive = candidates.all().get(2);
    assertThat(excessive.selectable()).isFalse();
    assertThat(excessive.extraPermissionIds()).containsExactly(4L);
    assertThatThrownBy(() -> candidates.requireSelectable(40))
        .isInstanceOf(DomainException.class)
        .hasMessage("该角色附带无关权限，请先配置最小权限角色。");
    assertThatThrownBy(() -> candidates.requireSelectable(1))
        .isInstanceOf(DomainException.class)
        .hasMessage("请选择包含申请权限的业务角色。")
        .extracting(e -> ((DomainException) e).reason())
        .isEqualTo(Reason.INVALID_REQUEST);
  }
}
