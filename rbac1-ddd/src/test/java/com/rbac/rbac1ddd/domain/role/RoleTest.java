package com.rbac.rbac1ddd.domain.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rbac.rbac1ddd.domain.permission.Permission;
import com.rbac.rbac1ddd.domain.permission.PermissionCatalog;
import com.rbac.rbac1ddd.domain.permission.PermissionType;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.shared.DomainException.Reason;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 纯领域测试：不启动 Spring、不连数据库，直接验证 RBAC1 需求中的角色规则。 */
class RoleTest {

  /** 测试用权限目录：1 用户菜单，2 用户新增按钮（属于 1），3 角色菜单，4 日志菜单。 */
  static final PermissionCatalog CATALOG =
      new PermissionCatalog(
          List.of(
              new Permission(1, 1, null, "用户管理", "user:view", PermissionType.MENU),
              new Permission(2, 1, 1L, "新增用户", "user:create", PermissionType.BUTTON),
              new Permission(3, 1, null, "角色管理", "role:view", PermissionType.MENU),
              new Permission(4, 1, null, "操作日志", "log:view", PermissionType.MENU)));

  /** 创建名称为「角色{id}」、说明为空的业务角色。 */
  static Role business(long id, Set<Long> direct, Set<Long> parents) {
    return new Role(id, "角色" + id, "", RoleType.BUSINESS, direct, parents);
  }

  @Test
  @DisplayName("验收1：多继承和多层继承获得全部父角色权限且重复权限只计算一次")
  void 多继承和多层继承获得全部父角色权限且重复权限只计算一次() {
    // 30 继承 20 和 21，20 继承 10；10 和 21 都有权限 1
    RoleHierarchy hierarchy =
        RoleHierarchy.of(
            List.of(
                business(10, Set.of(1L), Set.of()),
                business(20, Set.of(3L), Set.of(10L)),
                business(21, Set.of(1L, 4L), Set.of()),
                business(30, Set.of(), Set.of(20L, 21L))));

    List<EffectivePermission> effective = hierarchy.effectivePermissionsOf(30);

    assertThat(effective).extracting(EffectivePermission::permissionId).containsExactly(1L, 3L, 4L);
    assertThat(effective.get(0).sourceRoleIds()).containsExactlyInAnyOrder(10L, 21L);
    assertThat(effective).allMatch(p -> p.acquisition() == Acquisition.INHERITED);
  }

  @Test
  @DisplayName("验收4：同时直接配置和继承的权限标记为 BOTH")
  void 同时直接配置和继承的权限标记为BOTH() {
    RoleHierarchy hierarchy =
        RoleHierarchy.of(
            List.of(business(10, Set.of(1L), Set.of()), business(20, Set.of(1L), Set.of(10L))));

    assertThat(hierarchy.effectivePermissionsOf(20).get(0).acquisition()).isEqualTo(Acquisition.BOTH);
    assertThat(hierarchy.effectivePermissionsOf(10).get(0).acquisition()).isEqualTo(Acquisition.DIRECT);
  }

  @Test
  @DisplayName("创建业务角色时校验权限存在和按钮所属菜单")
  void 创建角色时校验父角色和按钮所属菜单() {
    Role parent = business(10, Set.of(1L), Set.of());
    RoleHierarchy hierarchy = RoleHierarchy.of(List.of(parent));

    Role role = Role.createBusiness(config(Set.of(2L), Set.of(10L)), hierarchy, CATALOG);
    assertThat(role.getId()).isNull();
    assertThat(role.getType()).isEqualTo(RoleType.BUSINESS);
    assertThat(role.getDirectPermissionIds()).containsExactly(2L);
    role.assignId(99);
    assertThat(role.getId()).isEqualTo(99L);
    assertThatThrownBy(() -> role.assignId(100)).isInstanceOf(IllegalStateException.class);

    assertRejected(
        () -> Role.createBusiness(config(Set.of(2L), Set.of()), hierarchy, CATALOG),
        Reason.INVALID_REQUEST,
        "角色「新名称」的按钮「新增用户」缺少所属菜单「用户管理」。");
    assertRejected(
        () -> Role.createBusiness(config(Set.of(9L), Set.of()), hierarchy, CATALOG),
        Reason.INVALID_REQUEST,
        "选择的权限不存在。");
  }

  @Test
  @DisplayName("验收2：拒绝自继承")
  void 拒绝自继承() {
    Role role = business(10, Set.of(), Set.of());
    RoleHierarchy hierarchy = RoleHierarchy.of(List.of(role));

    assertRejected(
        () -> role.reconfigure(config(Set.of(), Set.of(10L)), hierarchy, CATALOG),
        Reason.INVALID_REQUEST,
        "不能继承自身或形成循环继承。");
  }

  @Test
  @DisplayName("验收2：拒绝循环继承且原配置不变")
  void 拒绝循环继承且原配置不变() {
    Role parent = business(10, Set.of(1L), Set.of());
    Role child = business(20, Set.of(), Set.of(10L));
    RoleHierarchy hierarchy = RoleHierarchy.of(List.of(parent, child));

    assertRejected(
        () -> parent.reconfigure(config(Set.of(1L), Set.of(20L)), hierarchy, CATALOG),
        Reason.INVALID_REQUEST,
        "不能继承自身或形成循环继承。");
    assertThat(parent.getParentRoleIds()).isEmpty();
    assertThat(parent.getName()).isEqualTo("角色10");
  }

  @Test
  @DisplayName("验收2：管理员角色不能作为父角色")
  void 管理员角色不能作为父角色() {
    Role admin = new Role(1L, "总管理员", "", RoleType.TOTAL_ADMIN, Set.of(), Set.of());
    Role role = business(10, Set.of(), Set.of());
    RoleHierarchy hierarchy = RoleHierarchy.of(List.of(admin, role));

    assertRejected(
        () -> role.reconfigure(config(Set.of(), Set.of(1L)), hierarchy, CATALOG),
        Reason.INVALID_REQUEST,
        "父角色必须是当前存在的业务角色。");
  }

  @Test
  @DisplayName("RBAC0：预置管理员角色不能修改或删除")
  void 管理员角色不能修改或删除() {
    Role admin = new Role(1L, "总管理员", "", RoleType.TOTAL_ADMIN, Set.of(), Set.of());
    RoleHierarchy hierarchy = RoleHierarchy.of(List.of(admin));

    assertRejected(
        () -> admin.reconfigure(config(Set.of(), Set.of()), hierarchy, CATALOG),
        Reason.FORBIDDEN,
        "预置管理角色只读，不能修改或删除。");
    assertRejected(
        () -> admin.ensureDeletable(0, hierarchy), Reason.FORBIDDEN, "预置管理角色只读，不能修改或删除。");
  }

  @Test
  @DisplayName("验收5：不能新增已通过继承获得的直接权限")
  void 不能新增已通过继承获得的直接权限() {
    Role parent = business(10, Set.of(1L), Set.of());
    Role child = business(20, Set.of(), Set.of());
    RoleHierarchy hierarchy = RoleHierarchy.of(List.of(parent, child));

    assertRejected(
        () -> child.reconfigure(config(Set.of(1L), Set.of(10L)), hierarchy, CATALOG),
        Reason.INVALID_REQUEST,
        "权限「用户管理」已通过继承获得，不能新增重复直接配置。");
  }

  @Test
  @DisplayName("验收6：已有直接权限后再继承同一权限可以保留")
  void 已有直接权限后再继承同一权限可以保留() {
    Role parent = business(10, Set.of(1L), Set.of());
    Role child = business(20, Set.of(1L), Set.of());
    RoleHierarchy hierarchy = RoleHierarchy.of(List.of(parent, child));

    child.reconfigure(config(Set.of(1L), Set.of(10L)), hierarchy, CATALOG);

    assertThat(child.getParentRoleIds()).containsExactly(10L);
  }

  @Test
  @DisplayName("验收8：父角色去掉菜单会导致子角色按钮缺少菜单时拒绝修改")
  void 父角色去掉菜单会导致子角色按钮缺少菜单时拒绝修改() {
    Role parent = business(10, Set.of(1L), Set.of());
    Role child = business(20, Set.of(2L), Set.of(10L));
    RoleHierarchy hierarchy = RoleHierarchy.of(List.of(parent, child));

    assertRejected(
        () -> parent.reconfigure(config(Set.of(3L), Set.of()), hierarchy, CATALOG),
        Reason.INVALID_REQUEST,
        "角色「角色20」的按钮「新增用户」缺少所属菜单「用户管理」。");
  }

  @Test
  @DisplayName("修改成功后替换为最终配置并返回差异")
  void 修改成功后替换为最终配置并返回差异() {
    Role a = business(10, Set.of(1L), Set.of());
    Role b = business(11, Set.of(3L), Set.of());
    Role role = business(20, Set.of(4L), Set.of(10L));
    RoleHierarchy hierarchy = RoleHierarchy.of(List.of(a, b, role));

    RoleChange change = role.reconfigure(config(Set.of(2L), Set.of(11L, 10L)), hierarchy, CATALOG);

    assertThat(role.getName()).isEqualTo("新名称");
    assertThat(role.getDirectPermissionIds()).containsExactly(2L);
    assertThat(role.getParentRoleIds()).containsExactly(10L, 11L);
    assertThat(change.nameChanged()).isTrue();
    assertThat(change.descriptionChanged()).isFalse();
    assertThat(change.permissionsChanged()).isTrue();
    assertThat(change.parentsChanged()).isTrue();
    assertThat(change.isEmpty()).isFalse();
  }

  @Test
  @DisplayName("配置不变时差异为空")
  void 配置不变时差异为空() {
    Role role = new Role(10L, "新名称", "", RoleType.BUSINESS, Set.of(1L), Set.of());
    RoleChange change = role.reconfigure(config(Set.of(1L), Set.of()), RoleHierarchy.of(List.of(role)), CATALOG);
    assertThat(change.isEmpty()).isTrue();
  }

  @Test
  @DisplayName("验收3：移除一个继承来源后其他来源提供的权限仍然有效")
  void 移除一个来源后其他来源提供的权限仍然有效() {
    Role a = business(10, Set.of(1L), Set.of());
    Role b = business(11, Set.of(1L), Set.of());
    Role role = business(20, Set.of(), Set.of(10L, 11L));
    RoleHierarchy hierarchy = RoleHierarchy.of(List.of(a, b, role));

    role.reconfigure(config(Set.of(), Set.of(11L)), hierarchy, CATALOG);
    RoleHierarchy after = hierarchy.withConfiguration(20, role.getDirectPermissionIds(), role.getParentRoleIds());

    assertThat(after.effectivePermissionIds(20)).containsExactly(1L);
  }

  @Test
  @DisplayName("验收7：仍有继承关系或关联用户的角色不能删除")
  void 删除规则() {
    Role parent = business(10, Set.of(), Set.of());
    Role child = business(20, Set.of(), Set.of(10L));
    Role alone = business(30, Set.of(1L), Set.of());
    RoleHierarchy hierarchy = RoleHierarchy.of(List.of(parent, child, alone));

    assertRejected(
        () -> alone.ensureDeletable(2, hierarchy), Reason.CONFLICT, "该角色仍关联 2 个用户，请先撤销用户关联。");
    assertRejected(
        () -> parent.ensureDeletable(0, hierarchy), Reason.CONFLICT, "该角色仍存在父角色或子角色，请先解除继承关系。");
    assertRejected(
        () -> child.ensureDeletable(0, hierarchy), Reason.CONFLICT, "该角色仍存在父角色或子角色，请先解除继承关系。");
    alone.ensureDeletable(0, hierarchy);
  }

  @Test
  @DisplayName("RBAC0：分配角色时只能选择存在的业务角色")
  void 分配角色时只能选择存在的业务角色() {
    Role admin = new Role(1L, "总管理员", "", RoleType.TOTAL_ADMIN, Set.of(), Set.of());
    RoleHierarchy hierarchy = RoleHierarchy.of(List.of(admin, business(10, Set.of(), Set.of())));

    assertThat(hierarchy.requireBusinessRoles(List.of(10L))).containsExactly(10L);
    assertRejected(() -> hierarchy.requireBusinessRoles(List.of(99L)), Reason.NOT_FOUND, "所选角色中有角色不存在。");
    assertRejected(() -> hierarchy.requireBusinessRoles(List.of(1L)), Reason.INVALID_REQUEST, "这里只能分配或撤销业务角色。");
  }

  @Test
  @DisplayName("RBAC0：权限中心新登记的权限直接授予总管理员角色")
  void 权限中心新登记的权限直接授予总管理员角色() {
    Role totalAdmin = new Role(1L, "总管理员", "", RoleType.TOTAL_ADMIN, Set.of(1L), Set.of());

    totalAdmin.grantNewlyRegisteredPermission(5L);

    assertThat(totalAdmin.getDirectPermissionIds()).containsExactly(1L, 5L);
  }

  /** 创建名称固定为「新名称」、说明为空的角色配置，只改变直接权限和父角色。 */
  static RoleConfiguration config(Set<Long> permissions, Set<Long> parents) {
    return new RoleConfiguration("新名称", "", permissions, parents);
  }

  /** 断言操作被领域规则拒绝，并核对拒绝原因和中文提示。 */
  static void assertRejected(Runnable action, Reason reason, String message) {
    assertThatThrownBy(action::run)
        .isInstanceOf(DomainException.class)
        .hasMessage(message)
        .extracting(e -> ((DomainException) e).reason())
        .isEqualTo(reason);
  }
}
