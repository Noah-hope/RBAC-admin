package com.rbac.rbac1ddd.domain.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rbac.rbac1ddd.domain.permission.Permission;
import com.rbac.rbac1ddd.domain.permission.PermissionCatalog;
import com.rbac.rbac1ddd.domain.permission.PermissionType;
import com.rbac.rbac1ddd.domain.role.Acquisition;
import com.rbac.rbac1ddd.domain.role.EffectivePermission;
import com.rbac.rbac1ddd.domain.role.Role;
import com.rbac.rbac1ddd.domain.role.RoleConfiguration;
import com.rbac.rbac1ddd.domain.role.RoleHierarchy;
import com.rbac.rbac1ddd.domain.role.RoleType;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.shared.DomainException.Reason;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 纯领域测试：角色创建申请的提交校验、父角色快照、有效权限预览和状态流转。 */
class RoleCreationRequestTest {

  /** 权限：1 课程菜单，2 审核课程按钮（属于 1）。 */
  static final PermissionCatalog CATALOG =
      new PermissionCatalog(
          List.of(
              new Permission(1, 1, null, "课程管理", "course:view", PermissionType.MENU),
              new Permission(2, 1, 1L, "审核课程", "course:review", PermissionType.BUTTON)));

  /** 申请人（部门管理员）。 */
  static final long MANAGER = 5L;

  /** 创建业务角色。 */
  static Role business(long id, String name, Set<Long> direct, Set<Long> parents) {
    return new Role(id, name, "", RoleType.BUSINESS, direct, parents);
  }

  /** 申请继承父角色 10（拥有菜单 1）并直接配置按钮 2 的角色。 */
  static RoleCreationRequest submitted(RoleHierarchy hierarchy) {
    return RoleCreationRequest.submit(
        MANAGER,
        RoleConfiguration.of("审核助理", "协助审核", List.of(2L), List.of(10L)),
        hierarchy,
        CATALOG);
  }

  @Test
  @DisplayName("验收11：申请保存父角色名称快照，预览中继承权限显示来源，直接权限没有来源")
  void 提交申请保存父角色快照并预览有效权限() {
    RoleHierarchy hierarchy =
        RoleHierarchy.of(List.of(business(10, "原父角色", Set.of(1L), Set.of())));

    RoleCreationRequest request = submitted(hierarchy);

    assertThat(request.getStatus()).isEqualTo(RequestStatus.PENDING);
    assertThat(request.getPermissionIds()).containsExactly(2L);
    assertThat(request.getParentRoles()).containsExactly(new ParentRoleSnapshot(10L, "原父角色"));
    List<EffectivePermission> preview = request.previewIn(hierarchy).orElseThrow();
    assertThat(preview).extracting(EffectivePermission::permissionId).containsExactly(1L, 2L);
    assertThat(preview.get(0).acquisition()).isEqualTo(Acquisition.INHERITED);
    assertThat(preview.get(0).sourceRoleIds()).containsExactly(10L);
    assertThat(preview.get(1).acquisition()).isEqualTo(Acquisition.DIRECT);
    assertThat(preview.get(1).sourceRoleIds()).isEmpty();
  }

  @Test
  @DisplayName("验收11：提交申请按新建角色规则校验，父角色必须是业务角色，按钮必须有所属菜单")
  void 提交申请按新建角色规则校验() {
    RoleHierarchy hierarchy =
        RoleHierarchy.of(
            List.of(
                business(10, "父", Set.of(1L), Set.of()),
                new Role(1L, "总管理员", "", RoleType.TOTAL_ADMIN, Set.of(), Set.of())));

    assertRejected(
        () ->
            RoleCreationRequest.submit(
                MANAGER, RoleConfiguration.of("越权", "", List.of(), List.of(1L)), hierarchy, CATALOG),
        "父角色必须是当前存在的业务角色。");
    assertRejected(
        () ->
            RoleCreationRequest.submit(
                MANAGER, RoleConfiguration.of("重复", "", List.of(1L), List.of(10L)), hierarchy, CATALOG),
        "权限「课程管理」已通过继承获得，不能新增重复直接配置。");
    assertRejected(
        () ->
            RoleCreationRequest.submit(
                MANAGER, RoleConfiguration.of("缺菜单", "", List.of(2L), List.of()), hierarchy, CATALOG),
        "角色「缺菜单」的按钮「审核课程」缺少所属菜单「课程管理」。");
  }

  @Test
  @DisplayName("验收12：预览按父角色最新配置计算，父角色后来获得的权限与直接配置重叠时为 BOTH")
  void 预览使用父角色最新配置() {
    RoleHierarchy before = RoleHierarchy.of(List.of(business(10, "父", Set.of(), Set.of())));
    RoleCreationRequest request =
        RoleCreationRequest.submit(
            MANAGER, RoleConfiguration.of("申请角色", "", List.of(1L), List.of(10L)), before, CATALOG);

    RoleHierarchy after = RoleHierarchy.of(List.of(business(10, "父", Set.of(1L), Set.of())));

    assertThat(request.previewIn(after).orElseThrow().get(0).acquisition())
        .isEqualTo(Acquisition.BOTH);
  }

  @Test
  @DisplayName("验收11：父角色被删除后保留名称快照，标记为不存在，无法预览有效权限")
  void 父角色删除后无法预览() {
    RoleCreationRequest request =
        submitted(RoleHierarchy.of(List.of(business(10, "待删除", Set.of(1L), Set.of()))));
    RoleHierarchy deleted = RoleHierarchy.of(List.of());

    Optional<List<EffectivePermission>> preview = request.previewIn(deleted);

    assertThat(preview).isEmpty();
    assertThat(deleted.contains(10L)).isFalse();
    assertThat(request.getParentRoles().get(0).name()).isEqualTo("待删除");
  }

  @Test
  @DisplayName("验收12：创建角色成功后申请转为已通过并记录实际创建的角色，已处理的申请不能再审核")
  void 审批通过和拒绝() {
    RoleHierarchy hierarchy = RoleHierarchy.of(List.of(business(10, "父", Set.of(1L), Set.of())));
    RoleCreationRequest approved = submitted(hierarchy);
    RoleCreationRequest rejected = submitted(hierarchy);

    approved.approve(30L, 1L);
    rejected.reject(1L);

    assertThat(approved.getStatus()).isEqualTo(RequestStatus.APPROVED);
    assertThat(approved.getCreatedRoleId()).isEqualTo(30L);
    assertThat(approved.getReviewerUserId()).isEqualTo(1L);
    assertThat(approved.getReviewedAt()).isNotNull();
    assertThat(rejected.getStatus()).isEqualTo(RequestStatus.REJECTED);
    assertThat(rejected.getCreatedRoleId()).isNull();
    assertThatThrownBy(approved::ensurePending)
        .isInstanceOf(DomainException.class)
        .hasMessage("申请已处理。")
        .extracting(e -> ((DomainException) e).reason())
        .isEqualTo(Reason.CONFLICT);
    assertThatThrownBy(() -> rejected.approve(31L, 1L)).isInstanceOf(DomainException.class);
  }

  /** 断言提交被拒绝为请求不符合要求，并检查提示。 */
  private static void assertRejected(Runnable action, String detail) {
    assertThatThrownBy(action::run)
        .isInstanceOf(DomainException.class)
        .hasMessage(detail)
        .extracting(e -> ((DomainException) e).reason())
        .isEqualTo(Reason.INVALID_REQUEST);
  }
}
