package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.*;

import com.rbac.rbac1.dto.ApiDtos.ApiInput;
import com.rbac.rbac1.dto.SessionDtos.SessionUser;
import com.rbac.rbac1.entity.*;
import com.rbac.rbac1.mapper.*;
import com.rbac.rbac1.service.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(
    properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({ApiService.class, OperationLogService.class, PermissionCheckService.class, RolePermissionService.class})
class ApiPersistenceTest {
  @Autowired PermissionCheckService checks;
  @Autowired UserMapper users;
  @Autowired RoleMapper roles;
  @Autowired RolePermissionMapper rolePermissions;
  @Autowired UserRoleMapper assignments;
  @Autowired ApiService service;
  @Autowired ApplicationMapper apps;
  @Autowired PermissionMapper permissions;
  @Autowired ApiMapper apis;
  @Autowired ApiPermissionMapper links;
  private final SessionUser actor = new SessionUser(1, "admin", "管理员", null, "TOTAL_ADMIN");

  @Test
  void sharedApiSurvivesUnlinkAndCanBeRemovedIndependently() {
    var app = new BusinessApplication();
    app.name = "课程";
    app.code = "course";
    apps.save(app);
    var first = permission(app.id, "course:view");
    var second = permission(app.id, "course:edit");
    var api = service.create(actor, app.id, new ApiInput("listCourses", "GET", "/courses"));
    service.replaceLinks(app.id, first.id, List.of(api.id()));
    service.replaceLinks(app.id, second.id, List.of(api.id()));
    service.replaceLinks(app.id, first.id, List.of());
    assertThat(apis.existsById(api.id())).isTrue();
    assertThat(links.findByApiId(api.id()))
        .extracting(l -> l.permissionId)
        .containsExactly(second.id);
    service.delete(actor, app.id, api.id());
    assertThat(links.findByApiId(api.id())).isEmpty();
    assertThat(permissions.existsById(second.id)).isTrue();
  }

  @Test
  void crossApplicationLinksAreRejected() {
    var app = new BusinessApplication();
    app.name = "课程";
    app.code = "course";
    apps.save(app);
    var api = service.create(actor, app.id, new ApiInput("listCourses", "GET", "/courses"));
    assertThatThrownBy(() -> service.replaceLinks(app.id + 1, 1, List.of(api.id())))
        .isInstanceOf(ManagementFailure.class);
  }

  @Test
  void apiAllowsAnyLinkedEffectivePermissionAndDeniesAfterRevoke() {
    var app = new BusinessApplication();
    app.name = "课程";
    app.code = "course";
    apps.save(app);
    var permission = permission(app.id, "course:view");
    var api = service.create(actor, app.id, new ApiInput("listCourses", "GET", "/courses"));
    service.replaceLinks(app.id, permission.id, List.of(api.id()));
    var user = new UserAccount();
    user.username = "teacher";
    user.name = "教师";
    user.passwordHash = "hash";
    user.status = "ACTIVE";
    users.save(user);
    var role = new Role();
    role.name = "教师";
    role.description = "";
    role.roleType = "BUSINESS";
    roles.save(role);
    var link = new RolePermission();
    link.roleId = role.id;
    link.permissionId = permission.id;
    rolePermissions.save(link);
    var assignment = new UserRole();
    assignment.userId = user.id;
    assignment.roleId = role.id;
    assignments.save(assignment);
    assertThat(checks.checkApi(user.id, "course", "listCourses").allowed()).isTrue();
    assignments.delete(assignment);
    assignments.flush();
    assertThat(checks.checkApi(user.id, "course", "listCourses").allowed()).isFalse();
    assertThatThrownBy(() -> checks.checkApi(user.id, "course", "missing"))
        .isInstanceOf(ManagementFailure.class);
  }

  private Permission permission(long app, String code) {
    var p = new Permission();
    p.applicationId = app;
    p.name = code;
    p.code = code;
    p.type = "MENU";
    return permissions.save(p);
  }
}
