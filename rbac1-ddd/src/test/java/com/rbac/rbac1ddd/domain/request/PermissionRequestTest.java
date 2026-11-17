package com.rbac.rbac1ddd.domain.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rbac.rbac1ddd.domain.role.Role;
import com.rbac.rbac1ddd.domain.role.RoleHierarchy;
import com.rbac.rbac1ddd.domain.role.RoleType;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.shared.DomainException.Reason;
import com.rbac.rbac1ddd.domain.user.ManagementRole;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 纯领域测试：权限申请的提交和状态流转。 */
class PermissionRequestTest {

  /** 申请人。 */
  static final long APPLICANT = 7L;

  /** 审核人。 */
  static final long REVIEWER = 1L;

  /** 申请人提交课程审核按钮（11，属于菜单 10）的申请，申请人已通过继承拥有菜单 10。 */
  static PermissionRequest pending() {
    return PermissionRequest.submit(
        APPLICANT,
        11L,
        "  需要审核课程  ",
        RequestCatalogTest.catalog(Set.of(10L), ManagementRole.NONE),
        false);
  }

  /** 角色 20 继承菜单角色 10 并直接配置按钮 11，是可选的候选角色。 */
  static ApprovalCandidates candidates() {
    RoleHierarchy hierarchy =
        RoleHierarchy.of(
            List.of(
                new Role(10L, "菜单", "", RoleType.BUSINESS, Set.of(10L), Set.of()),
                new Role(20L, "审核", "", RoleType.BUSINESS, Set.of(11L), Set.of(10L))));
    return ApprovalCandidates.evaluate(
        RequestCatalogTest.CATALOG.get(11L), Set.of(10L), hierarchy);
  }

  @Test
  @DisplayName("RBAC0：提交申请保存去掉首尾空白的理由，状态为待审核")
  void 提交申请为待审核() {
    PermissionRequest request = pending();

    assertThat(request.getStatus()).isEqualTo(RequestStatus.PENDING);
    assertThat(request.getReason()).isEqualTo("需要审核课程");
    assertThat(request.getPermissionId()).isEqualTo(11L);
    assertThat(request.getCreatedAt()).isNotNull();
    assertThat(request.getReviewerUserId()).isNull();
  }

  @Test
  @DisplayName("RBAC0：只有申请人本人能撤回待审核申请，撤回后不能再处理")
  void 申请人撤回待审核申请() {
    PermissionRequest request = pending();

    assertRejected(() -> request.withdrawBy(99L), Reason.NOT_FOUND, "申请不存在。");
    request.withdrawBy(APPLICANT);

    assertThat(request.getStatus()).isEqualTo(RequestStatus.WITHDRAWN);
    assertRejected(() -> request.withdrawBy(APPLICANT), Reason.CONFLICT, "申请已处理。");
    assertRejected(() -> request.reject(REVIEWER), Reason.CONFLICT, "申请已处理。");
  }

  @Test
  @DisplayName("验收9：审批通过记录所选角色和审核人，附带无关权限的角色不能选择")
  void 审批通过记录角色和审核人() {
    PermissionRequest request = pending();

    assertRejected(
        () -> request.approve(10L, REVIEWER, candidates()),
        Reason.INVALID_REQUEST,
        "请选择包含申请权限的业务角色。");
    assertThat(request.getStatus()).isEqualTo(RequestStatus.PENDING);
    request.approve(20L, REVIEWER, candidates());

    assertThat(request.getStatus()).isEqualTo(RequestStatus.APPROVED);
    assertThat(request.getApprovedRoleId()).isEqualTo(20L);
    assertThat(request.getReviewerUserId()).isEqualTo(REVIEWER);
    assertThat(request.getReviewedAt()).isNotNull();
    assertRejected(() -> request.ensurePending(), Reason.CONFLICT, "申请已处理。");
  }

  @Test
  @DisplayName("RBAC0：审批拒绝不分配角色，记录审核人")
  void 审批拒绝() {
    PermissionRequest request = pending();

    request.reject(REVIEWER);

    assertThat(request.getStatus()).isEqualTo(RequestStatus.REJECTED);
    assertThat(request.getApprovedRoleId()).isNull();
    assertThat(request.getReviewerUserId()).isEqualTo(REVIEWER);
  }

  @Test
  @DisplayName("验收10：权限已满足时待审核申请转为已满足，其他历史状态不变")
  void 只有待审核申请会被结算为已满足() {
    PermissionRequest waiting = pending();
    PermissionRequest rejected = pending();
    rejected.reject(REVIEWER);

    assertThat(waiting.settleWith(Set.of(10L))).isFalse();
    assertThat(waiting.settleWith(Set.of(10L, 11L))).isTrue();
    assertThat(waiting.getStatus()).isEqualTo(RequestStatus.SATISFIED);
    assertThat(waiting.getReviewerUserId()).isNull();
    assertThat(rejected.settleWith(Set.of(10L, 11L))).isFalse();
    assertThat(rejected.getStatus()).isEqualTo(RequestStatus.REJECTED);
  }

  /** 断言操作被领域规则拒绝，并检查原因和提示。 */
  private static void assertRejected(Runnable action, Reason reason, String detail) {
    assertThatThrownBy(action::run)
        .isInstanceOf(DomainException.class)
        .hasMessage(detail)
        .extracting(e -> ((DomainException) e).reason())
        .isEqualTo(reason);
  }
}
