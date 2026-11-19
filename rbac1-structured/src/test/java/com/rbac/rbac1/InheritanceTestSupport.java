package com.rbac.rbac1;

import com.rbac.rbac1.dto.RoleDTO;
import com.rbac.rbac1.entity.BusinessApplication;
import com.rbac.rbac1.entity.Permission;
import com.rbac.rbac1.entity.Role;
import com.rbac.rbac1.entity.RoleInheritance;
import com.rbac.rbac1.entity.RolePermission;
import com.rbac.rbac1.entity.UserAccount;
import com.rbac.rbac1.entity.UserRole;
import com.rbac.rbac1.mapper.ApplicationMapper;
import com.rbac.rbac1.mapper.DepartmentMapper;
import com.rbac.rbac1.mapper.OperationLogMapper;
import com.rbac.rbac1.mapper.PermissionMapper;
import com.rbac.rbac1.mapper.PermissionRequestMapper;
import com.rbac.rbac1.mapper.RoleCreationRequestInheritanceMapper;
import com.rbac.rbac1.mapper.RoleCreationRequestMapper;
import com.rbac.rbac1.mapper.RoleInheritanceMapper;
import com.rbac.rbac1.mapper.RoleMapper;
import com.rbac.rbac1.mapper.RolePermissionMapper;
import com.rbac.rbac1.mapper.UserMapper;
import com.rbac.rbac1.mapper.UserRoleMapper;
import com.rbac.rbac1.service.ApiService;
import com.rbac.rbac1.service.DepartmentService;
import com.rbac.rbac1.service.OperationLogService;
import com.rbac.rbac1.service.PermissionCheckService;
import com.rbac.rbac1.service.PermissionRequestService;
import com.rbac.rbac1.service.RequestStatusService;
import com.rbac.rbac1.service.RoleCreationRequestService;
import com.rbac.rbac1.service.RolePermissionService;
import com.rbac.rbac1.service.RoleService;
import com.rbac.rbac1.service.SessionUser;
import com.rbac.rbac1.service.UserAuthorizationService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * 角色继承相关测试的公共基类。
 *
 * <p>用 {@code @DataJpaTest} 只加载 JPA 和继承规则涉及的服务，由 Hibernate 按实体建表；
 * 提供直接写表的造数方法，让各测试只关注被测的服务行为。
 */
@DataJpaTest(
    properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"},
    showSql = false)
@Import({
  RoleService.class,
  RolePermissionService.class,
  RequestStatusService.class,
  OperationLogService.class,
  DepartmentService.class,
  UserAuthorizationService.class,
  PermissionRequestService.class,
  RoleCreationRequestService.class,
  PermissionCheckService.class,
  ApiService.class
})
abstract class InheritanceTestSupport {

  @Autowired
  RoleService service;

  @Autowired
  RoleMapper roles;

  @Autowired
  PermissionMapper permissions;

  @Autowired
  RolePermissionMapper grants;

  @Autowired
  RoleInheritanceMapper inheritance;

  @Autowired
  UserMapper users;

  @Autowired
  UserRoleMapper assignments;

  @Autowired
  ApplicationMapper apps;

  @Autowired
  DepartmentMapper departments;

  @Autowired
  PermissionRequestService requests;

  @Autowired
  PermissionRequestMapper requestRows;

  @Autowired
  RoleCreationRequestService proposals;

  @Autowired
  RoleCreationRequestMapper proposalRows;

  @Autowired
  RoleCreationRequestInheritanceMapper proposalParents;

  @Autowired
  PermissionCheckService checks;

  @Autowired
  UserAuthorizationService authorizations;

  @Autowired
  ApiService apis;

  @Autowired
  OperationLogMapper logs;

  /** 执行管理操作的总管理员。 */
  final SessionUser root = new SessionUser(1000, "admin", "管理员", null, "TOTAL_ADMIN");

  /**
   * 直接写表创建业务角色，并直接配置给定的权限。
   *
   * @param name 角色名称
   * @param ids 直接配置的权限 ID
   * @return 已保存的角色
   */
  Role role(String name, Long... ids) {
    Role r = new Role();
    r.setName(name);
    r.setDescription("");
    r.setRoleType("BUSINESS");
    roles.save(r);
    for (Long id : ids) {
      RolePermission g = new RolePermission();
      g.setRoleId(r.getId());
      g.setPermissionId(id);
      grants.save(g);
    }
    return r;
  }

  /** 直接写表让子角色继承给定的父角色，不经过服务层校验。 */
  void inherits(Role child, Role... parents) {
    for (Role parent : parents) {
      RoleInheritance link = new RoleInheritance();
      link.setRoleId(child.getId());
      link.setInheritedRoleId(parent.getId());
      inheritance.save(link);
    }
  }

  /** 创建名称与编码相同的业务应用。 */
  BusinessApplication app(String code) {
    BusinessApplication a = new BusinessApplication();
    a.setCode(code);
    a.setName(code);
    return apps.save(a);
  }

  /**
   * 创建权限：不指定菜单时为菜单权限，指定菜单时为该菜单下的按钮权限。
   *
   * @param app 所属应用
   * @param code 权限编码，同时用作名称
   * @param menu 按钮所属菜单，为 {@code null} 时创建菜单
   * @return 已保存的权限
   */
  Permission permission(BusinessApplication app, String code, Permission menu) {
    Permission p = new Permission();
    p.setApplicationId(app.getId());
    p.setName(code);
    p.setCode(code);
    p.setType(menu == null ? "MENU" : "BUTTON");
    p.setParentId(menu == null ? null : menu.getId());
    return permissions.save(p);
  }

  /** 创建启用的普通用户（无管理身份、无部门），返回对应的会话用户。 */
  SessionUser user(String name) {
    UserAccount u = new UserAccount();
    u.setUsername(name);
    u.setName(name);
    u.setPasswordHash("hash");
    u.setStatus("ACTIVE");
    users.save(u);
    return new SessionUser(u.getId(), name, name, null, "NONE");
  }

  /** 直接写表为用户分配角色。 */
  void assign(SessionUser user, Role role) {
    UserRole a = new UserRole();
    a.setUserId(user.id());
    a.setRoleId(role.getId());
    assignments.save(a);
  }

  /**
   * 构造角色配置请求。
   *
   * @param name 角色名称
   * @param direct 直接配置的权限 ID
   * @param parents 继承的父角色 ID
   * @return 角色配置请求
   */
  RoleDTO input(String name, List<Long> direct, List<Long> parents) {
    RoleDTO r = new RoleDTO();
    r.setName(name);
    r.setPermissionIds(direct);
    r.setInheritedRoleIds(parents);
    return r;
  }
}
