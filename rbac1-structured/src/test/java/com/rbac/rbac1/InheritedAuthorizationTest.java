package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbac.rbac1.dto.ApiDTO;
import com.rbac.rbac1.entity.BusinessApplication;
import com.rbac.rbac1.entity.Permission;
import com.rbac.rbac1.entity.Role;
import com.rbac.rbac1.entity.UserAccount;
import com.rbac.rbac1.service.SessionUser;
import com.rbac.rbac1.vo.ApiVO;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 继承权限参与鉴权的测试：接口检查、前端权限检查和我的权限都包含继承获得的权限，
 * 且继承变化后下一次检查立即反映。
 */
class InheritedAuthorizationTest extends InheritanceTestSupport {

  @Test
  @DisplayName("验收3：继承获得的接口和菜单权限立即生效，解除继承后立即失去，且不给用户分配父角色")
  void inheritedApiAndFrontendPermissionsReflectRevocationWithoutAssigningAncestors() {
    BusinessApplication app = app("course");
    Permission menu = permission(app, "course:view", null);
    Role parent = role("来源", menu.getId());
    Role child = role("分配角色");
    inherits(child, parent);
    SessionUser user = user("teacher");
    assign(user, child);
    ApiVO api = apis.create(root, app.getId(), new ApiDTO("listCourses", "GET", "/courses"));
    apis.replaceLinks(app.getId(), menu.getId(), List.of(api.id()));
    assertThat(checks.checkPermission(user.id(), "course", "course:view").allowed()).isTrue();
    assertThat(checks.checkApi(user.id(), "course", "listCourses").allowed()).isTrue();
    assertThat(checks.current(user, "course").roles()).containsExactly(child.getId().toString());
    JsonNode mine = new ObjectMapper().valueToTree(checks.mine(user.id()));
    assertThat(
            mine.path("applications")
                .get(0)
                .path("menus")
                .get(0)
                .path("sourceRoles")
                .findValuesAsText("name"))
        .containsExactly("来源");
    assertThat(assignments.findAll()).extracting(a -> a.getRoleId()).containsExactly(child.getId());
    service.update(root, child.getId(), input("分配角色", List.of(), List.of()));
    assertThat(checks.checkApi(user.id(), "course", "listCourses").allowed()).isFalse();
    assertThat(checks.checkPermission(user.id(), "course", "course:view").allowed()).isFalse();
    assertThat(checks.current(user, "course").roles()).isEmpty();
  }

  @Test
  @DisplayName("验收3：解除一个父角色后其他来源提供的权限保留；停用账号不再有任何权限")
  void anotherInheritedSourceSurvivesAndDisabledAccountsGetNoRights() {
    BusinessApplication app = app("course");
    Permission menu = permission(app, "course:view", null);
    Role a = role("A", menu.getId());
    Role b = role("B", menu.getId());
    Role child = role("子");
    inherits(child, a, b);
    SessionUser user = user("teacher");
    assign(user, child);
    service.update(root, child.getId(), input("子", List.of(), List.of(b.getId())));
    assertThat(checks.checkPermission(user.id(), "course", "course:view").allowed()).isTrue();
    UserAccount entity = users.findById(user.id()).orElseThrow();
    entity.setStatus("DISABLED");
    users.flush();
    assertThat(checks.checkPermission(user.id(), "course", "course:view").allowed()).isFalse();
  }
}
