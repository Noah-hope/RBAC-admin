package com.rbac.rbac1ddd.domain.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbac.rbac1ddd.domain.permission.Permission;
import com.rbac.rbac1ddd.domain.permission.PermissionCatalog;
import com.rbac.rbac1ddd.domain.permission.PermissionType;
import com.rbac.rbac1ddd.domain.role.Role;
import com.rbac.rbac1ddd.domain.role.RoleHierarchy;
import com.rbac.rbac1ddd.domain.role.RoleType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 用户聚合单元测试：不启动 Spring，验证有效权限、管理身份和业务角色的替换与批量变更。 */
class UserTest {

  // 1 总管理员，2 部门管理员，10 父业务角色（权限 100），11 子业务角色（权限 101，继承 10）
  private final RoleHierarchy hierarchy =
      RoleHierarchy.of(
          List.of(
              new Role(1L, "总管理员", "", RoleType.TOTAL_ADMIN, Set.of(), Set.of()),
              new Role(2L, "部门管理员", "", RoleType.DEPARTMENT_ADMIN, Set.of(), Set.of()),
              new Role(10L, "父", "", RoleType.BUSINESS, Set.of(100L), Set.of()),
              new Role(11L, "子", "", RoleType.BUSINESS, Set.of(101L), Set.of(10L))));

  /** 创建部门 3 下指定状态、持有指定角色的用户。 */
  private static User user(UserStatus status, Long... roleIds) {
    return new User(7L, "zhang", "张三", 3L, "hash", status, Set.of(roleIds));
  }

  @Test
  @DisplayName("验收1：用户有效权限包含角色继承获得的父角色权限")
  void 用户有效权限是全部角色有效权限的并集() {
    assertThat(user(UserStatus.ACTIVE, 11L).effectivePermissionIds(hierarchy))
        .containsExactly(100L, 101L);
  }

  @Test
  @DisplayName("验收4：用户每项有效权限列出直接配置该权限的来源角色，中间传递角色不计为来源")
  void 用户有效权限列出直接配置来源() {
    assertThat(user(UserStatus.ACTIVE, 11L).permissionSources(hierarchy))
        .containsExactly(Map.entry(100L, Set.of(10L)), Map.entry(101L, Set.of(11L)));
    assertThat(user(UserStatus.ACTIVE, 10L, 11L).permissionSources(hierarchy))
        .containsEntry(100L, Set.of(10L));
    assertThat(user(UserStatus.DISABLED, 11L).permissionSources(hierarchy)).isEmpty();
  }

  @Test
  @DisplayName("RBAC0：停用用户没有任何权限")
  void 停用用户没有任何权限() {
    assertThat(user(UserStatus.DISABLED, 11L).effectivePermissionIds(hierarchy)).isEmpty();
  }

  @Test
  @DisplayName("RBAC0：管理身份由预置管理员角色决定")
  void 管理身份由预置管理员角色决定() {
    assertThat(user(UserStatus.ACTIVE, 1L, 2L).managementRole(hierarchy))
        .isEqualTo(ManagementRole.TOTAL_ADMIN);
    assertThat(user(UserStatus.ACTIVE, 2L).managementRole(hierarchy))
        .isEqualTo(ManagementRole.DEPARTMENT_ADMIN);
    assertThat(user(UserStatus.ACTIVE, 10L).managementRole(hierarchy)).isEqualTo(ManagementRole.NONE);
  }

  @Test
  @DisplayName("RBAC0：替换业务角色只变更差异并保留管理员角色")
  void 替换业务角色只变更差异并保留管理员角色() {
    User user = user(UserStatus.ACTIVE, 2L, 10L);

    RoleAssignmentChange change = user.replaceBusinessRoles(Set.of(11L), hierarchy);

    assertThat(change.added()).containsExactly(11L);
    assertThat(change.removed()).containsExactly(10L);
    assertThat(user.getRoleIds()).containsExactly(2L, 11L);
  }

  @Test
  @DisplayName("RBAC0：提交空列表解除全部业务角色")
  void 空列表解除全部业务角色() {
    User user = user(UserStatus.ACTIVE, 2L, 10L, 11L);

    RoleAssignmentChange change = user.replaceBusinessRoles(Set.of(), hierarchy);

    assertThat(change.removed()).containsExactly(10L, 11L);
    assertThat(user.getRoleIds()).containsExactly(2L);
  }

  @Test
  @DisplayName("RBAC0：提交相同角色列表时没有变化")
  void 提交相同列表时没有变化() {
    assertThat(user(UserStatus.ACTIVE, 10L).replaceBusinessRoles(Set.of(10L), hierarchy).isEmpty())
        .isTrue();
  }

  @Test
  @DisplayName("RBAC0：批量分配和撤销只处理需要变化的角色")
  void 批量分配和撤销只处理需要变化的角色() {
    User user = user(UserStatus.ACTIVE, 10L);

    assertThat(user.grantBusinessRoles(Set.of(10L, 11L)).added()).containsExactly(11L);
    assertThat(user.revokeBusinessRoles(Set.of(10L)).removed()).containsExactly(10L);
    assertThat(user.revokeBusinessRoles(Set.of(10L)).isEmpty()).isTrue();
    assertThat(user.getRoleIds()).containsExactly(11L);
  }

  @Test
  @DisplayName("验收1：目标应用的角色列表包含通过继承为该应用提供权限的直接角色")
  void 目标应用的角色包含通过继承提供权限的角色() {
    // 权限 100 属于应用 1，权限 101 属于应用 2；角色 11 继承 10，因此同时为两个应用提供权限
    PermissionCatalog catalog =
        new PermissionCatalog(
            List.of(
                new Permission(100, 1, null, "课程管理", "course:view", PermissionType.MENU),
                new Permission(101, 2, null, "成绩管理", "score:view", PermissionType.MENU)));
    User user = user(UserStatus.ACTIVE, 1L, 11L);
    assertThat(user.rolesGrantingPermissionsIn(1, hierarchy, catalog)).containsExactly(11L);
    assertThat(user.rolesGrantingPermissionsIn(3, hierarchy, catalog)).isEmpty();
    assertThat(user(UserStatus.DISABLED, 11L).rolesGrantingPermissionsIn(1, hierarchy, catalog))
        .isEmpty();
  }

  @Test
  @DisplayName("RBAC0：首次启动创建的总管理员启用、没有部门并持有总管理员角色")
  void 首次启动创建的总管理员() {
    User admin = User.firstTotalAdmin("admin", "digest", 1L);
    assertThat(admin.getId()).isNull();
    assertThat(admin.getName()).isEqualTo("总管理员");
    assertThat(admin.getDepartmentId()).isNull();
    assertThat(admin.isActive()).isTrue();
    assertThat(admin.managementRole(hierarchy)).isEqualTo(ManagementRole.TOTAL_ADMIN);
    admin.assignId(5);
    assertThat(admin.getId()).isEqualTo(5L);
  }

  @Test
  @DisplayName("RBAC0：新增用户属于所选部门、没有角色，保存后回填 ID")
  void 新增用户属于所选部门且没有角色() {
    User created = User.create("li", "李四", 3L, "digest", UserStatus.DISABLED);

    assertThat(created.getId()).isNull();
    assertThat(created.getUsername()).isEqualTo("li");
    assertThat(created.getName()).isEqualTo("李四");
    assertThat(created.getDepartmentId()).isEqualTo(3L);
    assertThat(created.getPasswordHash()).isEqualTo("digest");
    assertThat(created.getStatus()).isEqualTo(UserStatus.DISABLED);
    assertThat(created.getRoleIds()).isEmpty();
  }

  @Test
  @DisplayName("RBAC0：修改用户名、姓名和账号状态只在实际变化时返回 true")
  void 修改用户资料只在实际变化时返回true() {
    User user = user(UserStatus.ACTIVE, 10L);

    assertThat(user.changeUsername("zhang")).isFalse();
    assertThat(user.changeUsername("zhang3")).isTrue();
    assertThat(user.rename("张三")).isFalse();
    assertThat(user.rename("张三丰")).isTrue();
    assertThat(user.changeStatus(UserStatus.ACTIVE)).isFalse();
    assertThat(user.changeStatus(UserStatus.DISABLED)).isTrue();
    assertThat(user.getUsername()).isEqualTo("zhang3");
    assertThat(user.getName()).isEqualTo("张三丰");
    assertThat(user.isActive()).isFalse();
  }

  @Test
  @DisplayName("RBAC0：跨部门调动保留业务角色和管理员角色")
  void 跨部门调动保留业务角色() {
    User user = user(UserStatus.ACTIVE, 2L, 10L);

    assertThat(user.transferTo(3L)).isFalse();
    assertThat(user.transferTo(4L)).isTrue();

    assertThat(user.getDepartmentId()).isEqualTo(4L);
    assertThat(user.getRoleIds()).containsExactly(2L, 10L);
  }

  @Test
  @DisplayName("RBAC0：任免部门管理员只增删部门管理员角色，重复任免没有变化")
  void 任免部门管理员只增删部门管理员角色() {
    User user = user(UserStatus.ACTIVE, 10L);

    assertThat(user.isDepartmentAdministrator(hierarchy)).isFalse();
    assertThat(user.changeDepartmentAdministrator(true, 2L)).isTrue();
    assertThat(user.changeDepartmentAdministrator(true, 2L)).isFalse();
    assertThat(user.isDepartmentAdministrator(hierarchy)).isTrue();
    assertThat(user.getRoleIds()).containsExactly(2L, 10L);
    assertThat(user.changeDepartmentAdministrator(false, 2L)).isTrue();
    assertThat(user.changeDepartmentAdministrator(false, 2L)).isFalse();
    assertThat(user.getRoleIds()).containsExactly(10L);
  }
}
