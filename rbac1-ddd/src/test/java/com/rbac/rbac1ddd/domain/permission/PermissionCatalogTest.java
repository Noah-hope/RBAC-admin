package com.rbac.rbac1ddd.domain.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 权限目录单元测试：不启动 Spring，验证按应用筛选权限。 */
class PermissionCatalogTest {

  // 应用 1：菜单 10、按钮 11；应用 2：菜单 20
  private final PermissionCatalog catalog =
      new PermissionCatalog(
          List.of(
              new Permission(10, 1, null, "课程管理", "course:view", PermissionType.MENU),
              new Permission(11, 1, 10L, "新增课程", "course:create", PermissionType.BUTTON),
              new Permission(20, 2, null, "成绩管理", "score:view", PermissionType.MENU),
              new Permission(12, 1, null, "排课管理", "schedule:view", PermissionType.MENU),
              new Permission(13, 1, 12L, "调整排课", "schedule:update", PermissionType.BUTTON)));

  @Test
  @DisplayName("RBAC0：只列出目标应用内的权限编码，按权限 ID 升序")
  void 只列出目标应用内的权限编码() {
    assertThat(catalog.codesIn(1, Set.of(20L, 11L, 10L)))
        .containsExactly("course:view", "course:create");
    assertThat(catalog.codesIn(2, Set.of(10L))).isEmpty();
  }

  @Test
  @DisplayName("RBAC0：判断一组权限中是否有属于目标应用的权限")
  void 判断是否有属于目标应用的权限() {
    assertThat(catalog.anyIn(2, Set.of(10L, 20L))).isTrue();
    assertThat(catalog.anyIn(2, Set.of(10L, 11L))).isFalse();
  }

  @Test
  @DisplayName("RBAC0：权限目录按应用列出全部菜单和按钮，按权限 ID 升序")
  void 按应用列出全部权限() {
    assertThat(catalog.permissionsIn(1))
        .extracting(Permission::id)
        .containsExactly(10L, 11L, 12L, 13L);
    assertThat(catalog.permissionsIn(2)).extracting(Permission::id).containsExactly(20L);
    assertThat(catalog.permissionsIn(3)).isEmpty();
  }

  @Test
  @DisplayName("RBAC0：从已获权限中列出目标应用的菜单及菜单下已获得的按钮")
  void 从已获权限中列出菜单和按钮() {
    Set<Long> granted = Set.of(13L, 20L, 12L, 10L);
    assertThat(catalog.menusIn(1, granted)).extracting(Permission::id).containsExactly(10L, 12L);
    assertThat(catalog.menusIn(2, granted)).extracting(Permission::id).containsExactly(20L);
    assertThat(catalog.buttonsOf(10, granted)).isEmpty();
    assertThat(catalog.buttonsOf(12, granted)).extracting(Permission::id).containsExactly(13L);
  }
}
