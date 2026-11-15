package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.*;

import com.rbac.rbac1.service.ManagementFailure;
import java.util.*;
import org.junit.jupiter.api.Test;

class RoleInheritanceRulesTest extends InheritanceTestSupport {
  @Test
  void inheritedMenuAllowsDirectButtonWithoutDuplicatingTheMenu() {
    var app = app("course");
    var menu = permission(app, "course:view", null);
    var button = permission(app, "course:edit", menu);
    var parent = role("父", menu.id);
    var child = service.create(root, input("子", List.of(button.id), List.of(parent.id)));
    assertThat(child.inheritedRoles()).extracting(r -> r.id()).containsExactly(parent.id);
    assertThat(child.effectivePermissions())
        .extracting(p -> p.id())
        .containsExactly(menu.id, button.id);
  }

  @Test
  void rejectsSelfCyclesManagementParentsAndNewDuplicateGrants() {
    var app = app("course");
    var menu = permission(app, "course:view", null);
    var a = role("A", menu.id);
    var b = role("B");
    inherits(b, a);
    assertThatThrownBy(
            () -> service.update(root, a.id, input("A", List.of(menu.id), List.of(b.id))))
        .isInstanceOf(ManagementFailure.class);
    assertThat(service.get(root, a.id).inheritedRoles()).isEmpty();
    assertThatThrownBy(
            () -> service.update(root, a.id, input("A", List.of(menu.id), List.of(a.id))))
        .isInstanceOf(ManagementFailure.class);
    var admin = role("管理员");
    admin.roleType = "TOTAL_ADMIN";
    roles.flush();
    assertThatThrownBy(() -> service.create(root, input("错", List.of(), List.of(admin.id))))
        .isInstanceOf(ManagementFailure.class);
    assertThatThrownBy(() -> service.create(root, input("重复", List.of(menu.id), List.of(a.id))))
        .isInstanceOf(ManagementFailure.class);
    assertThatThrownBy(() -> service.create(root, input("缺失", List.of(), List.of(9999L))))
        .isInstanceOf(ManagementFailure.class);
  }

  @Test
  void parentChangeCannotOrphanAnIndirectDescendantsButton() {
    var app = app("course");
    var menu = permission(app, "course:view", null);
    var button = permission(app, "course:edit", menu);
    var a = role("A", menu.id);
    var b = role("B");
    var c = role("C", button.id);
    inherits(b, a);
    inherits(c, b);
    assertThatThrownBy(() -> service.update(root, a.id, input("A", List.of(), List.of())))
        .isInstanceOf(ManagementFailure.class)
        .hasMessageContaining("C")
        .hasMessageContaining("course:edit");
    assertThat(service.get(root, a.id).permissionIds()).containsExactly(menu.id);
    var alternative = role("其他来源", menu.id);
    inherits(c, alternative);
    service.update(root, a.id, input("A", List.of(), List.of()));
    assertThat(service.get(root, c.id).effectivePermissions())
        .extracting(p -> p.id())
        .containsExactly(menu.id, button.id);
  }

  @Test
  void removingExistingDirectOverlapRetainsInheritedRight() {
    var app = app("course");
    var menu = permission(app, "course:view", null);
    var a = role("A", menu.id);
    var b = role("B", menu.id);
    inherits(b, a);
    service.update(root, b.id, input("B", List.of(menu.id), List.of(a.id)));
    var result = service.update(root, b.id, input("B", List.of(), List.of(a.id)));
    assertThat(result.permissionIds()).isEmpty();
    assertThat(result.effectivePermissions())
        .extracting(p -> p.acquisition())
        .containsExactly("INHERITED");
  }

  @Test
  void deletionRequiresUnlinkingBothInheritanceDirections() {
    var a = role("A");
    var b = role("B");
    inherits(b, a);
    assertThatThrownBy(() -> service.delete(root, a.id)).isInstanceOf(ManagementFailure.class);
    assertThatThrownBy(() -> service.delete(root, b.id)).isInstanceOf(ManagementFailure.class);
    service.update(root, b.id, input("B", List.of(), List.of()));
    service.delete(root, a.id);
    assertThat(roles.existsById(a.id)).isFalse();
  }
}
