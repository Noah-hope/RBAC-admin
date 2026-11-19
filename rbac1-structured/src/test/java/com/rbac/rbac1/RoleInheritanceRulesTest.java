package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rbac.rbac1.entity.BusinessApplication;
import com.rbac.rbac1.entity.Permission;
import com.rbac.rbac1.entity.Role;
import com.rbac.rbac1.service.ManagementFailure;
import com.rbac.rbac1.vo.RoleDetailsVO;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 角色继承的保存规则测试：继承校验、按钮所属菜单检查、直接配置与继承重叠以及删除限制。 */
class RoleInheritanceRulesTest extends InheritanceTestSupport {

  @Test
  @DisplayName("验收8：菜单通过继承获得时可以直接配置其下按钮，菜单不重复配置")
  void inheritedMenuAllowsDirectButtonWithoutDuplicatingTheMenu() {
    BusinessApplication app = app("course");
    Permission menu = permission(app, "course:view", null);
    Permission button = permission(app, "course:edit", menu);
    Role parent = role("父", menu.getId());
    RoleDetailsVO child =
        service.create(root, input("子", List.of(button.getId()), List.of(parent.getId())));
    assertThat(child.inheritedRoles()).extracting(r -> r.id()).containsExactly(parent.getId());
    assertThat(child.effectivePermissions())
        .extracting(p -> p.id())
        .containsExactly(menu.getId(), button.getId());
  }

  @Test
  @DisplayName("验收2：拒绝自继承、循环继承和管理员父角色，也拒绝重复配置已继承权限和不存在的父角色")
  void rejectsSelfCyclesManagementParentsAndNewDuplicateGrants() {
    BusinessApplication app = app("course");
    Permission menu = permission(app, "course:view", null);
    Role a = role("A", menu.getId());
    Role b = role("B");
    inherits(b, a);
    assertThatThrownBy(
            () ->
                service.update(
                    root, a.getId(), input("A", List.of(menu.getId()), List.of(b.getId()))))
        .isInstanceOf(ManagementFailure.class);
    assertThat(service.get(root, a.getId()).inheritedRoles()).isEmpty();
    assertThatThrownBy(
            () ->
                service.update(
                    root, a.getId(), input("A", List.of(menu.getId()), List.of(a.getId()))))
        .isInstanceOf(ManagementFailure.class);
    Role admin = role("管理员");
    admin.setRoleType("TOTAL_ADMIN");
    roles.flush();
    assertThatThrownBy(() -> service.create(root, input("错", List.of(), List.of(admin.getId()))))
        .isInstanceOf(ManagementFailure.class);
    assertThatThrownBy(
            () -> service.create(root, input("重复", List.of(menu.getId()), List.of(a.getId()))))
        .isInstanceOf(ManagementFailure.class);
    assertThatThrownBy(() -> service.create(root, input("缺失", List.of(), List.of(9999L))))
        .isInstanceOf(ManagementFailure.class);
  }

  @Test
  @DisplayName("验收8：父角色去掉菜单会使间接子角色的按钮失去菜单时保存失败，其他来源仍提供菜单时可以保存")
  void parentChangeCannotOrphanAnIndirectDescendantsButton() {
    BusinessApplication app = app("course");
    Permission menu = permission(app, "course:view", null);
    Permission button = permission(app, "course:edit", menu);
    Role a = role("A", menu.getId());
    Role b = role("B");
    Role c = role("C", button.getId());
    inherits(b, a);
    inherits(c, b);
    assertThatThrownBy(() -> service.update(root, a.getId(), input("A", List.of(), List.of())))
        .isInstanceOf(ManagementFailure.class)
        .hasMessageContaining("C")
        .hasMessageContaining("course:edit");
    assertThat(service.get(root, a.getId()).permissionIds()).containsExactly(menu.getId());
    Role alternative = role("其他来源", menu.getId());
    inherits(c, alternative);
    service.update(root, a.getId(), input("A", List.of(), List.of()));
    assertThat(service.get(root, c.getId()).effectivePermissions())
        .extracting(p -> p.id())
        .containsExactly(menu.getId(), button.getId());
  }

  @Test
  @DisplayName("验收6：取消与继承重叠的直接配置后，继承来源仍提供该权限")
  void removingExistingDirectOverlapRetainsInheritedRight() {
    BusinessApplication app = app("course");
    Permission menu = permission(app, "course:view", null);
    Role a = role("A", menu.getId());
    Role b = role("B", menu.getId());
    inherits(b, a);
    service.update(root, b.getId(), input("B", List.of(menu.getId()), List.of(a.getId())));
    RoleDetailsVO result =
        service.update(root, b.getId(), input("B", List.of(), List.of(a.getId())));
    assertThat(result.permissionIds()).isEmpty();
    assertThat(result.effectivePermissions())
        .extracting(p -> p.acquisition())
        .containsExactly("INHERITED");
  }

  @Test
  @DisplayName("验收7：角色仍继承其他角色或被其他角色继承时不能删除，解除继承后可以删除")
  void deletionRequiresUnlinkingBothInheritanceDirections() {
    Role a = role("A");
    Role b = role("B");
    inherits(b, a);
    assertThatThrownBy(() -> service.delete(root, a.getId())).isInstanceOf(ManagementFailure.class);
    assertThatThrownBy(() -> service.delete(root, b.getId())).isInstanceOf(ManagementFailure.class);
    service.update(root, b.getId(), input("B", List.of(), List.of()));
    service.delete(root, a.getId());
    assertThat(roles.existsById(a.getId())).isFalse();
  }
}
