package com.rbac.rbac1ddd.domain.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rbac.rbac1ddd.domain.app.BusinessApplication;
import com.rbac.rbac1ddd.domain.permission.Permission;
import com.rbac.rbac1ddd.domain.permission.PermissionCatalog;
import com.rbac.rbac1ddd.domain.permission.PermissionType;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.shared.DomainException.Reason;
import com.rbac.rbac1ddd.domain.user.ManagementRole;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 纯领域测试：权限申请目录和提交条件。 */
class RequestCatalogTest {

  /** 应用：1 课程，2 权限中心。 */
  static final List<BusinessApplication> APPLICATIONS =
      List.of(
          new BusinessApplication(1L, "course", "课程"),
          new BusinessApplication(2L, BusinessApplication.PERMISSION_CENTER, "权限管理中心"));

  /**
   * 权限：10 课程菜单，11、12 课程按钮（属于 10），20 课程第二个菜单；
   * 30 权限中心用户菜单，31 新增用户按钮（属于 30），40 权限中心部门维护菜单（管理员专属职责）。
   */
  static final PermissionCatalog CATALOG =
      new PermissionCatalog(
          List.of(
              new Permission(10, 1, null, "课程管理", "course:view", PermissionType.MENU),
              new Permission(11, 1, 10L, "审核课程", "course:review", PermissionType.BUTTON),
              new Permission(12, 1, 10L, "删除课程", "course:delete", PermissionType.BUTTON),
              new Permission(20, 1, null, "学期管理", "term:view", PermissionType.MENU),
              new Permission(30, 2, null, "用户与部门", "user:view", PermissionType.MENU),
              new Permission(31, 2, 30L, "新增用户", "user:create", PermissionType.BUTTON),
              new Permission(40, 2, null, "应用与权限", "application:view", PermissionType.MENU)));

  /** 构造指定已拥有权限和管理身份的申请目录。 */
  static RequestCatalog catalog(Set<Long> owned, ManagementRole role) {
    return new RequestCatalog(CATALOG, APPLICATIONS, owned, role);
  }

  @Test
  @DisplayName("RBAC0：未获得菜单时只列出菜单，不列出其下按钮")
  void 未获得菜单时只列出菜单() {
    List<RequestCatalog.MenuGroup> groups = catalog(Set.of(), ManagementRole.NONE).groupsIn(1);

    assertThat(groups).extracting(g -> g.menu().id()).containsExactly(10L, 20L);
    assertThat(groups.get(0).permissions()).extracting(Permission::id).containsExactly(10L);
  }

  @Test
  @DisplayName("验收9：菜单通过继承获得后只列出其下未获得的按钮，已拥有的权限不再列出")
  void 已获得菜单后只列出未获得按钮() {
    List<RequestCatalog.MenuGroup> groups =
        catalog(Set.of(10L, 12L, 20L), ManagementRole.NONE).groupsIn(1);

    assertThat(groups).hasSize(1);
    assertThat(groups.get(0).menu().id()).isEqualTo(10L);
    assertThat(groups.get(0).permissions()).extracting(Permission::id).containsExactly(11L);
  }

  @Test
  @DisplayName("RBAC0：权限中心只对管理员开放用户、授权和审计职责，普通用户看不到")
  void 权限中心只对管理员开放部分职责() {
    assertThat(catalog(Set.of(), ManagementRole.NONE).groupsIn(2)).isEmpty();
    List<RequestCatalog.MenuGroup> groups =
        catalog(Set.of(), ManagementRole.DEPARTMENT_ADMIN).groupsIn(2);
    assertThat(groups).extracting(g -> g.menu().id()).containsExactly(30L);
  }

  @Test
  @DisplayName("验收9：可以申请继承所得菜单下的按钮")
  void 可以申请已获得菜单下的按钮() {
    assertThat(catalog(Set.of(10L), ManagementRole.NONE).requireRequestable(11, false).id())
        .isEqualTo(11L);
  }

  @Test
  @DisplayName("RBAC0：已拥有、已有待审核申请或缺少所属菜单时不能申请")
  void 已拥有或待审核或缺少菜单时不能申请() {
    assertRejected(() -> catalog(Set.of(10L), ManagementRole.NONE).requireRequestable(10, false),
        Reason.CONFLICT, "已拥有、已有待审核申请，或尚未获得所属菜单。");
    assertRejected(() -> catalog(Set.of(), ManagementRole.NONE).requireRequestable(11, false),
        Reason.CONFLICT, "已拥有、已有待审核申请，或尚未获得所属菜单。");
    assertRejected(() -> catalog(Set.of(), ManagementRole.NONE).requireRequestable(10, true),
        Reason.CONFLICT, "已拥有、已有待审核申请，或尚未获得所属菜单。");
  }

  @Test
  @DisplayName("RBAC0：不能通过业务角色申请管理员专属职责，权限不存在时提示不存在")
  void 管理员专属职责和不存在的权限不能申请() {
    assertRejected(
        () -> catalog(Set.of(), ManagementRole.TOTAL_ADMIN).requireRequestable(40, false),
        Reason.FORBIDDEN,
        "该权限属于固定管理员职责，不能通过业务角色申请。");
    assertRejected(() -> catalog(Set.of(), ManagementRole.NONE).requireRequestable(30, false),
        Reason.FORBIDDEN, "该权限属于固定管理员职责，不能通过业务角色申请。");
    assertRejected(() -> catalog(Set.of(), ManagementRole.NONE).requireRequestable(99, false),
        Reason.NOT_FOUND, "权限不存在。");
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
