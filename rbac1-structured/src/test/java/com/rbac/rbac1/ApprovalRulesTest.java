package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbac.rbac1.service.ApprovalRules;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 权限申请审核规则的单元测试：判断批准候选角色时申请人会额外获得哪些无关权限。
 *
 * <p>不启动 Spring，直接调用 {@link ApprovalRules} 的纯计算方法。
 */
class ApprovalRulesTest {

  @Test
  @DisplayName("验收9：被申请权限的所属菜单和申请人已拥有的权限不算无关权限")
  void necessaryMenuAndAlreadyOwnedRightsAreNotExtraPrivileges() {
    assertThat(ApprovalRules.extraPermissions(Set.of(1L, 2L, 3L), Set.of(3L), 2L, 1L)).isEmpty();
  }

  @Test
  @DisplayName("验收9：候选角色带有无关权限时列出这些权限，该角色不适合用于审批")
  void unrelatedRightsMakeRoleUnsuitableForApproval() {
    assertThat(ApprovalRules.extraPermissions(Set.of(1L, 2L, 4L), Set.of(), 2L, 1L))
        .containsExactly(4L);
  }
}
