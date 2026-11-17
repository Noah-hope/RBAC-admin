package com.rbac.rbac1ddd.domain.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rbac.rbac1ddd.domain.api.Api;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.shared.DomainException.Reason;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 权限定义聚合单元测试：不启动 Spring，验证菜单与按钮的上下级规则和接口关联规则（沿用 RBAC0 规则）。 */
class PermissionDefinitionTest {

  /** 应用 2 的菜单「所有课程」。 */
  private final PermissionDefinition menu =
      new PermissionDefinition(
          101L, 2L, null, "所有课程", "course:viewAll", PermissionType.MENU, Set.of());

  /** 应用 2 的按钮「审核课程」，属于菜单 101。 */
  private final PermissionDefinition button =
      new PermissionDefinition(
          102L, 2L, 101L, "审核课程", "course:review", PermissionType.BUTTON, Set.of());

  /** 应用 3 的菜单。 */
  private final PermissionDefinition otherMenu =
      new PermissionDefinition(
          201L, 3L, null, "图书", "book:view", PermissionType.MENU, Set.of());

  @Test
  @DisplayName("RBAC0：登记菜单时不能填写上级，登记按钮时选择同一应用的菜单")
  void 登记菜单和按钮() {
    PermissionDefinition newMenu =
        PermissionDefinition.register(2L, "我的课程", "course:mine", PermissionType.MENU, null, null);
    PermissionDefinition newButton =
        PermissionDefinition.register(
            2L, "新增课程", "course:create", PermissionType.BUTTON, 101L, menu);

    assertThat(newMenu.getId()).isNull();
    assertThat(newMenu.getParentId()).isNull();
    assertThat(newMenu.isMenu()).isTrue();
    assertThat(newMenu.getApiIds()).isEmpty();
    assertThat(newButton.getParentId()).isEqualTo(101L);
    assertThat(newButton.isMenu()).isFalse();
    assertThat(newButton.getApplicationId()).isEqualTo(2L);

    assertRejected(
        () ->
            PermissionDefinition.register(
                2L, "我的课程", "course:mine", PermissionType.MENU, 101L, menu),
        "菜单权限的上级必须为空。");
    assertRejected(
        () ->
            PermissionDefinition.register(
                2L, "我的课程", "course:mine", PermissionType.MENU, 999L, null),
        "菜单权限的上级必须为空。");
  }

  @Test
  @DisplayName("RBAC0：按钮的上级必须存在、必须是菜单且属于同一应用")
  void 按钮的上级必须是同一应用的菜单() {
    String detail = "按钮权限的上级必须是同一应用的菜单。";
    assertRejected(
        () ->
            PermissionDefinition.register(
                2L, "新增课程", "course:create", PermissionType.BUTTON, null, null),
        detail);
    assertRejected(
        () ->
            PermissionDefinition.register(
                2L, "新增课程", "course:create", PermissionType.BUTTON, 999L, null),
        detail);
    assertRejected(
        () ->
            PermissionDefinition.register(
                2L, "新增课程", "course:create", PermissionType.BUTTON, 102L, button),
        detail);
    assertRejected(
        () ->
            PermissionDefinition.register(
                2L, "新增课程", "course:create", PermissionType.BUTTON, 201L, otherMenu),
        detail);
  }

  @Test
  @DisplayName("RBAC0：权限完整替换关联接口，清空关联不影响接口登记")
  void 权限完整替换关联接口() {
    Api first = new Api(1L, 2L, "listCourses", "GET", "/courses");
    Api second = new Api(2L, 2L, "reviewCourse", "POST", "/courses/{courseId}/review");

    menu.linkApis(List.of(2L, 1L), List.of(first, second));
    assertThat(menu.getApiIds()).containsExactly(1L, 2L);
    assertThatThrownBy(() -> menu.getApiIds().add(3L))
        .isInstanceOf(UnsupportedOperationException.class);

    menu.linkApis(List.of(2L), List.of(second));
    assertThat(menu.getApiIds()).containsExactly(2L);

    menu.unlinkApi(2L);
    assertThat(menu.getApiIds()).isEmpty();

    menu.linkApis(List.of(1L), List.of(first));
    menu.linkApis(List.of(), List.of());
    assertThat(menu.getApiIds()).isEmpty();
  }

  @Test
  @DisplayName("RBAC0：权限只能关联同一应用下已登记的接口，失败时原关联不变")
  void 只能关联同一应用下已登记的接口() {
    Api own = new Api(1L, 2L, "listCourses", "GET", "/courses");
    Api foreign = new Api(5L, 3L, "listBooks", "GET", "/books");
    menu.linkApis(List.of(1L), List.of(own));

    String detail = "请选择同一应用下已登记的API。";
    assertRejected(() -> menu.linkApis(List.of(1L, 9L), List.of(own)), detail);
    assertRejected(() -> menu.linkApis(List.of(5L), List.of(foreign)), detail);
    assertThat(menu.getApiIds()).containsExactly(1L);
  }

  @Test
  @DisplayName("RBAC0：修改权限名称和标识只在实际变化时返回 true")
  void 修改权限名称和标识只在实际变化时返回true() {
    assertThat(button.rename("审核课程")).isFalse();
    assertThat(button.rename("审核课程申请")).isTrue();
    assertThat(button.getName()).isEqualTo("审核课程申请");
    assertThat(button.recode("course:review")).isFalse();
    assertThat(button.recode("course:approve")).isTrue();
    assertThat(button.getCode()).isEqualTo("course:approve");
    assertThat(button.getParentId()).isEqualTo(101L);
    assertThat(button.getType()).isEqualTo(PermissionType.BUTTON);
  }

  /** 断言操作被拒绝为 INVALID_REQUEST，并带有给定提示。 */
  private static void assertRejected(Runnable action, String detail) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            DomainException.class,
            error -> assertThat(error.reason()).isEqualTo(Reason.INVALID_REQUEST))
        .hasMessage(detail);
  }
}
