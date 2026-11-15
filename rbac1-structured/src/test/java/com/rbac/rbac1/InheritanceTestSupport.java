package com.rbac.rbac1;

import com.rbac.rbac1.dto.RoleDtos.RoleInput;
import com.rbac.rbac1.dto.SessionDtos.SessionUser;
import com.rbac.rbac1.entity.*;
import com.rbac.rbac1.mapper.*;
import com.rbac.rbac1.service.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"}, showSql=false)
@Import({RoleService.class, RolePermissionService.class, RequestStatusService.class, OperationLogService.class,
    DepartmentService.class, UserAuthorizationService.class, PermissionRequestService.class,
    RoleCreationRequestService.class, PermissionCheckService.class, ApiService.class})
abstract class InheritanceTestSupport {
  @Autowired RoleService service;
  @Autowired RoleMapper roles;
  @Autowired PermissionMapper permissions;
  @Autowired RolePermissionMapper grants;
  @Autowired RoleInheritanceMapper inheritance;
  @Autowired UserMapper users;
  @Autowired UserRoleMapper assignments;
  @Autowired ApplicationMapper apps;
  @Autowired PermissionRequestService requests;
  @Autowired PermissionRequestMapper requestRows;
  @Autowired RoleCreationRequestService proposals;
  @Autowired RoleCreationRequestMapper proposalRows;
  @Autowired RoleCreationRequestInheritanceMapper proposalParents;
  @Autowired PermissionCheckService checks;
  @Autowired UserAuthorizationService authorizations;
  @Autowired ApiService apis;
  @Autowired OperationLogMapper logs;
  final SessionUser root = new SessionUser(1000, "admin", "管理员", null, "TOTAL_ADMIN");

  Role role(String name, Long... ids) {
    var r = new Role(); r.name=name; r.description=""; r.roleType="BUSINESS";
    roles.save(r);
    for (var id : ids) { var g=new RolePermission(); g.roleId=r.id; g.permissionId=id; grants.save(g); }
    return r;
  }
  void inherits(Role child, Role... parents) {
    for (var parent : parents) { var link=new RoleInheritance(); link.roleId=child.id; link.inheritedRoleId=parent.id; inheritance.save(link); }
  }
  BusinessApplication app(String code) {
    var a=new BusinessApplication(); a.code=code; a.name=code; return apps.save(a);
  }
  Permission permission(BusinessApplication app, String code, Permission menu) {
    var p=new Permission(); p.applicationId=app.id; p.name=code; p.code=code;
    p.type=menu==null ? "MENU" : "BUTTON"; p.parentId=menu==null ? null : menu.id;
    return permissions.save(p);
  }
  SessionUser user(String name) {
    var u=new UserAccount(); u.username=name; u.name=name; u.passwordHash="hash"; u.status="ACTIVE";
    users.save(u); return new SessionUser(u.id,name,name,null,"NONE");
  }
  void assign(SessionUser user, Role role) {
    var a=new UserRole(); a.userId=user.id(); a.roleId=role.id; assignments.save(a);
  }
  RoleInput input(String name, List<Long> direct, List<Long> parents) {
    var r=new RoleInput(); r.name=name; r.permissionIds=direct; r.inheritedRoleIds=parents;
    return r;
  }
}
