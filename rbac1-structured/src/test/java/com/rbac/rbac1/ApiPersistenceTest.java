package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rbac.rbac1.dto.ApiDTO;
import com.rbac.rbac1.entity.BusinessApplication;
import com.rbac.rbac1.entity.Permission;
import com.rbac.rbac1.entity.Role;
import com.rbac.rbac1.entity.RolePermission;
import com.rbac.rbac1.entity.UserAccount;
import com.rbac.rbac1.entity.UserRole;
import com.rbac.rbac1.mapper.ApiMapper;
import com.rbac.rbac1.mapper.ApiPermissionMapper;
import com.rbac.rbac1.mapper.ApplicationMapper;
import com.rbac.rbac1.mapper.PermissionMapper;
import com.rbac.rbac1.mapper.RoleMapper;
import com.rbac.rbac1.mapper.RolePermissionMapper;
import com.rbac.rbac1.mapper.UserMapper;
import com.rbac.rbac1.mapper.UserRoleMapper;
import com.rbac.rbac1.service.ApiService;
import com.rbac.rbac1.service.ManagementFailure;
import com.rbac.rbac1.service.OperationLogService;
import com.rbac.rbac1.service.PermissionCheckService;
import com.rbac.rbac1.service.RolePermissionService;
import com.rbac.rbac1.service.SessionUser;
import com.rbac.rbac1.vo.ApiVO;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * API 登记与 API 鉴权的持久化测试：API 与权限多对多关联，持有任一关联权限即可调用。
 *
 * <p>用 {@code @DataJpaTest} 只加载 JPA 和相关服务，由 Hibernate 按实体建表。
 */
@DataJpaTest(
    properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({
  ApiService.class,
  OperationLogService.class,
  PermissionCheckService.class,
  RolePermissionService.class
})
class ApiPersistenceTest {

  @Autowired
  PermissionCheckService checks;

  @Autowired
  UserMapper users;

  @Autowired
  RoleMapper roles;

  @Autowired
  RolePermissionMapper rolePermissions;

  @Autowired
  UserRoleMapper assignments;

  @Autowired
  ApiService service;

  @Autowired
  ApplicationMapper apps;

  @Autowired
  PermissionMapper permissions;

  @Autowired
  ApiMapper apis;

  @Autowired
  ApiPermissionMapper links;

  /** 执行登记操作的总管理员。 */
  private final SessionUser actor = new SessionUser(1, "admin", "管理员", null, "TOTAL_ADMIN");

  @Test
  @DisplayName("RBAC0：一个 API 可被多项权限共享，解除其中一项关联仍保留 API，删除 API 时只删除关联")
  void sharedApiSurvivesUnlinkAndCanBeRemovedIndependently() {
    BusinessApplication app = new BusinessApplication();
    app.setName("课程");
    app.setCode("course");
    apps.save(app);
    Permission first = permission(app.getId(), "course:view");
    Permission second = permission(app.getId(), "course:edit");
    ApiVO api = service.create(actor, app.getId(), new ApiDTO("listCourses", "GET", "/courses"));
    service.replaceLinks(app.getId(), first.getId(), List.of(api.id()));
    service.replaceLinks(app.getId(), second.getId(), List.of(api.id()));
    service.replaceLinks(app.getId(), first.getId(), List.of());
    assertThat(apis.existsById(api.id())).isTrue();
    assertThat(links.findByApiId(api.id()))
        .extracting(l -> l.getPermissionId())
        .containsExactly(second.getId());
    service.delete(actor, app.getId(), api.id());
    assertThat(links.findByApiId(api.id())).isEmpty();
    assertThat(permissions.existsById(second.getId())).isTrue();
  }

  @Test
  @DisplayName("RBAC0：不能把 API 关联到其他应用的权限")
  void crossApplicationLinksAreRejected() {
    BusinessApplication app = new BusinessApplication();
    app.setName("课程");
    app.setCode("course");
    apps.save(app);
    ApiVO api = service.create(actor, app.getId(), new ApiDTO("listCourses", "GET", "/courses"));
    assertThatThrownBy(() -> service.replaceLinks(app.getId() + 1, 1, List.of(api.id())))
        .isInstanceOf(ManagementFailure.class);
  }

  @Test
  @DisplayName("RBAC0：持有任一关联权限即可调用 API，撤销角色后立即拒绝，未登记的 API 报不存在")
  void apiAllowsAnyLinkedEffectivePermissionAndDeniesAfterRevoke() {
    BusinessApplication app = new BusinessApplication();
    app.setName("课程");
    app.setCode("course");
    apps.save(app);
    Permission permission = permission(app.getId(), "course:view");
    ApiVO api = service.create(actor, app.getId(), new ApiDTO("listCourses", "GET", "/courses"));
    service.replaceLinks(app.getId(), permission.getId(), List.of(api.id()));
    UserAccount user = new UserAccount();
    user.setUsername("teacher");
    user.setName("教师");
    user.setPasswordHash("hash");
    user.setStatus("ACTIVE");
    users.save(user);
    Role role = new Role();
    role.setName("教师");
    role.setDescription("");
    role.setRoleType("BUSINESS");
    roles.save(role);
    RolePermission link = new RolePermission();
    link.setRoleId(role.getId());
    link.setPermissionId(permission.getId());
    rolePermissions.save(link);
    UserRole assignment = new UserRole();
    assignment.setUserId(user.getId());
    assignment.setRoleId(role.getId());
    assignments.save(assignment);
    assertThat(checks.checkApi(user.getId(), "course", "listCourses").allowed()).isTrue();
    assignments.delete(assignment);
    assignments.flush();
    assertThat(checks.checkApi(user.getId(), "course", "listCourses").allowed()).isFalse();
    assertThatThrownBy(() -> checks.checkApi(user.getId(), "course", "missing"))
        .isInstanceOf(ManagementFailure.class);
  }

  /** 在给定应用下创建菜单权限，编码同时用作名称。 */
  private Permission permission(long app, String code) {
    Permission p = new Permission();
    p.setApplicationId(app);
    p.setName(code);
    p.setCode(code);
    p.setType("MENU");
    return permissions.save(p);
  }
}
