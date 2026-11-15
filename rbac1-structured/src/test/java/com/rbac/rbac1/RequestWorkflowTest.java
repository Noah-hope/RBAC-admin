package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.*;

import com.rbac.rbac1.dto.RequestDtos.*;
import com.rbac.rbac1.dto.RoleDtos.RoleInput;
import com.rbac.rbac1.dto.SessionDtos.SessionUser;
import com.rbac.rbac1.entity.*;
import com.rbac.rbac1.mapper.*;
import com.rbac.rbac1.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest(
    properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({
  RoleService.class,
  RequestStatusService.class,
  PermissionRequestService.class,
  RoleCreationRequestService.class,
  DepartmentService.class,
  OperationLogService.class,
  OperationLogQueryService.class
})
class RequestWorkflowTest {
  @Autowired OperationLogQueryService queryLogs;
  @Autowired OperationLogService operationLogs;
  @Autowired PermissionRequestService requests;
  @Autowired RoleCreationRequestService proposals;
  @Autowired RoleService roleService;
  @Autowired ApplicationMapper apps;
  @Autowired PermissionMapper permissions;
  @Autowired UserMapper users;
  @Autowired DepartmentMapper departments;
  @Autowired UserRoleMapper assignments;
  @Autowired RoleMapper roles;
  @Autowired OperationLogMapper logs;
  SessionUser root = new SessionUser(1, "admin", "管理员", null, "TOTAL_ADMIN");

  @Test
  void approvalAssignsExistingRoleAndRecordsTheRequest() {
    var user = user("teacher", null);
    var p = permission();
    var role = roleService.create(root, role("审核员", List.of(p.id)));
    var request = requests.submit(user, new PermissionRequestInput(p.id, "需要处理课程"));
    var result = requests.review(root, request.id(), new PermissionReview("APPROVE", role.id()));
    assertThat(result.status()).isEqualTo("APPROVED");
    assertThat(assignments.existsById(new UserRole.Key(user.id(), role.id()))).isTrue();
    assertThat(logs.findAll())
        .anyMatch(log -> log.action.equals("审核权限申请") && log.scope.equals("GLOBAL"));
  }

  @Test
  void withdrawalKeepsHistoryWithoutGrantingRole() {
    var user = user("teacher", null);
    var request = requests.submit(user, new PermissionRequestInput(permission().id, "需要课程"));
    assertThat(requests.withdraw(user, request.id()).status()).isEqualTo("WITHDRAWN");
    assertThat(requests.list(user, "MINE", null, 1, 20).items()).hasSize(1);
    assertThat(assignments.findAll()).isEmpty();
  }

  @Test
  void roleProposalRemainsPendingUntilActualCreationAndDoesNotAssign() {
    var department = new Department();
    department.name = "学院";
    departments.save(department);
    var user = user("manager", department.id);
    user =
        new SessionUser(user.id(), user.username(), user.name(), department.id, "DEPARTMENT_ADMIN");
    var proposal = proposals.submit(user, role("课程审核", List.of(permission().id)));
    assertThat(proposals.get(root, proposal.id()).status()).isEqualTo("PENDING");
    assertThat(roles.findAll()).isEmpty();
    var result =
        proposals.review(
            root,
            proposal.id(),
            new RoleCreationReview("APPROVE", role("课程审核", proposal.permissionIds())));
    assertThat(result.status()).isEqualTo("APPROVED");
    assertThat(roles.existsById(result.createdRoleId())).isTrue();
    assertThat(assignments.findAll()).isEmpty();
  }

  @Test
  void departmentLogUsesEventSnapshotAndHidesGlobalAndOtherTargets() {
    var first = new Department();
    first.name = "甲院";
    departments.save(first);
    var second = new Department();
    second.name = "乙院";
    departments.save(second);
    var alice = user("甲员工", first.id);
    var bob = user("乙员工", second.id);
    var aliceEntity = users.findById(alice.id()).orElseThrow();
    var bobEntity = users.findById(bob.id()).orElseThrow();
    operationLogs.recordUserChanges(root, "批量授权", "分配课程角色", List.of(aliceEntity, bobEntity));
    operationLogs.recordGlobalChange(root, "修改角色", "全局配置", "ROLE", 1, "角色");
    aliceEntity.departmentId = second.id;
    users.flush();
    var manager = new SessionUser(3, "manager", "部门管理员", first.id, "DEPARTMENT_ADMIN");
    var visible = queryLogs.list(manager, 1, 20, "");
    assertThat(visible.items()).hasSize(1);
    assertThat(visible.items().getFirst().targetNames()).containsExactly("甲员工");
    assertThat(queryLogs.list(manager, 1, 20, "乙员工").total()).isZero();
  }

  private SessionUser user(String name, Long dept) {
    var u = new UserAccount();
    u.username = name;
    u.name = name;
    u.passwordHash = "hash";
    u.status = "ACTIVE";
    u.departmentId = dept;
    users.save(u);
    return new SessionUser(u.id, u.username, u.name, dept, "NONE");
  }

  private Permission permission() {
    var app = new BusinessApplication();
    app.name = "课程";
    app.code = "course";
    apps.save(app);
    var p = new Permission();
    p.applicationId = app.id;
    p.name = "课程";
    p.code = "course:view";
    p.type = "MENU";
    return permissions.save(p);
  }

  private RoleInput role(String name, List<Long> ids) {
    var r = new RoleInput();
    r.name = name;
    r.permissionIds = ids;
    return r;
  }
}
