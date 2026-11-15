package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbac.rbac1.dto.ApiDtos.ApiInput;
import java.util.*;
import org.junit.jupiter.api.Test;

class InheritedAuthorizationTest extends InheritanceTestSupport {
  @Test
  void inheritedApiAndFrontendPermissionsReflectRevocationWithoutAssigningAncestors() {
    var app = app("course");
    var menu = permission(app, "course:view", null);
    var parent = role("来源", menu.id);
    var child = role("分配角色");
    inherits(child, parent);
    var user = user("teacher");
    assign(user, child);
    var api = apis.create(root, app.id, new ApiInput("listCourses", "GET", "/courses"));
    apis.replaceLinks(app.id, menu.id, List.of(api.id()));
    assertThat(checks.checkPermission(user.id(), "course", "course:view").allowed()).isTrue();
    assertThat(checks.checkApi(user.id(), "course", "listCourses").allowed()).isTrue();
    assertThat(checks.current(user, "course").roles()).containsExactly(child.id.toString());
    var mine = new ObjectMapper().valueToTree(checks.mine(user.id()));
    assertThat(
            mine.path("applications")
                .get(0)
                .path("menus")
                .get(0)
                .path("sourceRoles")
                .findValuesAsText("name"))
        .containsExactly("来源");
    assertThat(assignments.findAll()).extracting(a -> a.roleId).containsExactly(child.id);
    service.update(root, child.id, input("分配角色", List.of(), List.of()));
    assertThat(checks.checkApi(user.id(), "course", "listCourses").allowed()).isFalse();
    assertThat(checks.checkPermission(user.id(), "course", "course:view").allowed()).isFalse();
    assertThat(checks.current(user, "course").roles()).isEmpty();
  }

  @Test
  void anotherInheritedSourceSurvivesAndDisabledAccountsGetNoRights() {
    var app = app("course");
    var menu = permission(app, "course:view", null);
    var a = role("A", menu.id);
    var b = role("B", menu.id);
    var child = role("子");
    inherits(child, a, b);
    var user = user("teacher");
    assign(user, child);
    service.update(root, child.id, input("子", List.of(), List.of(b.id)));
    assertThat(checks.checkPermission(user.id(), "course", "course:view").allowed()).isTrue();
    var entity = users.findById(user.id()).orElseThrow();
    entity.status = "DISABLED";
    users.flush();
    assertThat(checks.checkPermission(user.id(), "course", "course:view").allowed()).isFalse();
  }
}
