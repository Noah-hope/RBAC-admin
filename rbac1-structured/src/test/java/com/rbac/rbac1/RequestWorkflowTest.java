package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbac.rbac1.dto.PermissionRequestDTO;
import com.rbac.rbac1.dto.PermissionReviewDTO;
import com.rbac.rbac1.dto.RoleCreationReviewDTO;
import com.rbac.rbac1.dto.RoleDTO;
import com.rbac.rbac1.entity.BusinessApplication;
import com.rbac.rbac1.entity.Department;
import com.rbac.rbac1.entity.Permission;
import com.rbac.rbac1.entity.UserAccount;
import com.rbac.rbac1.entity.UserRole;
import com.rbac.rbac1.mapper.ApplicationMapper;
import com.rbac.rbac1.mapper.DepartmentMapper;
import com.rbac.rbac1.mapper.OperationLogMapper;
import com.rbac.rbac1.mapper.PermissionMapper;
import com.rbac.rbac1.mapper.RoleMapper;
import com.rbac.rbac1.mapper.UserMapper;
import com.rbac.rbac1.mapper.UserRoleMapper;
import com.rbac.rbac1.service.DepartmentService;
import com.rbac.rbac1.service.OperationLogQueryService;
import com.rbac.rbac1.service.OperationLogService;
import com.rbac.rbac1.service.PermissionRequestService;
import com.rbac.rbac1.service.RequestStatusService;
import com.rbac.rbac1.service.RoleCreationRequestService;
import com.rbac.rbac1.service.RolePermissionService;
import com.rbac.rbac1.service.RoleService;
import com.rbac.rbac1.service.SessionUser;
import com.rbac.rbac1.vo.OperationLogVO;
import com.rbac.rbac1.vo.PageVO;
import com.rbac.rbac1.vo.PermissionRequestVO;
import com.rbac.rbac1.vo.RoleCreationRequestVO;
import com.rbac.rbac1.vo.RoleDetailsVO;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * 申请与审核流程的持久化测试：权限申请的批准和撤回、角色创建申请的审批，
 * 以及部门管理员按事件快照查看操作记录。
 */
@DataJpaTest(
    properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({
  RoleService.class,
  RolePermissionService.class,
  RequestStatusService.class,
  PermissionRequestService.class,
  RoleCreationRequestService.class,
  DepartmentService.class,
  OperationLogService.class,
  OperationLogQueryService.class
})
class RequestWorkflowTest {

  @Autowired
  OperationLogQueryService queryLogs;

  @Autowired
  OperationLogService operationLogs;

  @Autowired
  PermissionRequestService requests;

  @Autowired
  RoleCreationRequestService proposals;

  @Autowired
  RoleService roleService;

  @Autowired
  ApplicationMapper apps;

  @Autowired
  PermissionMapper permissions;

  @Autowired
  UserMapper users;

  @Autowired
  DepartmentMapper departments;

  @Autowired
  UserRoleMapper assignments;

  @Autowired
  RoleMapper roles;

  @Autowired
  OperationLogMapper logs;

  /** 执行审核操作的总管理员。 */
  SessionUser root = new SessionUser(1, "admin", "管理员", null, "TOTAL_ADMIN");

  @Test
  @DisplayName("RBAC0：批准权限申请时分配所选的已有角色，并留下全局操作记录")
  void approvalAssignsExistingRoleAndRecordsTheRequest() {
    SessionUser user = user("teacher", null);
    Permission p = permission();
    RoleDetailsVO role = roleService.create(root, role("审核员", List.of(p.getId())));
    PermissionRequestVO request =
        requests.submit(user, new PermissionRequestDTO(p.getId(), "需要处理课程"));
    PermissionRequestVO result =
        requests.review(root, request.id(), new PermissionReviewDTO("APPROVE", role.id()));
    assertThat(result.status()).isEqualTo("APPROVED");
    assertThat(assignments.existsById(new UserRole.Key(user.id(), role.id()))).isTrue();
    assertThat(logs.findAll())
        .anyMatch(log -> log.getAction().equals("审核权限申请") && log.getScope().equals("GLOBAL"));
  }

  @Test
  @DisplayName("RBAC0：撤回申请后保留历史记录，且不分配任何角色")
  void withdrawalKeepsHistoryWithoutGrantingRole() {
    SessionUser user = user("teacher", null);
    PermissionRequestVO request =
        requests.submit(user, new PermissionRequestDTO(permission().getId(), "需要课程"));
    assertThat(requests.withdraw(user, request.id()).status()).isEqualTo("WITHDRAWN");
    assertThat(requests.list(user, "MINE", null, 1, 20).items()).hasSize(1);
    assertThat(assignments.findAll()).isEmpty();
  }

  @Test
  @DisplayName("验收12：角色创建申请在实际创建角色前保持待审核，批准后创建角色但不自动分配给员工")
  void roleProposalRemainsPendingUntilActualCreationAndDoesNotAssign() {
    Department department = new Department();
    department.setName("学院");
    departments.save(department);
    SessionUser user = user("manager", department.getId());
    user =
        new SessionUser(
            user.id(), user.username(), user.name(), department.getId(), "DEPARTMENT_ADMIN");
    RoleCreationRequestVO proposal =
        proposals.submit(user, role("课程审核", List.of(permission().getId())));
    assertThat(proposals.get(root, proposal.id()).request().status()).isEqualTo("PENDING");
    assertThat(roles.findAll()).isEmpty();
    RoleCreationRequestVO result =
        proposals.review(
            root,
            proposal.id(),
            new RoleCreationReviewDTO("APPROVE", role("课程审核", proposal.permissionIds())));
    assertThat(result.status()).isEqualTo("APPROVED");
    assertThat(roles.existsById(result.createdRoleId())).isTrue();
    assertThat(assignments.findAll()).isEmpty();
  }

  @Test
  @DisplayName("RBAC0：部门管理员按事件发生时的部门快照查看操作记录，看不到全局操作和其他部门的对象")
  void departmentLogUsesEventSnapshotAndHidesGlobalAndOtherTargets() {
    Department first = new Department();
    first.setName("甲院");
    departments.save(first);
    Department second = new Department();
    second.setName("乙院");
    departments.save(second);
    SessionUser alice = user("甲员工", first.getId());
    SessionUser bob = user("乙员工", second.getId());
    UserAccount aliceEntity = users.findById(alice.id()).orElseThrow();
    UserAccount bobEntity = users.findById(bob.id()).orElseThrow();
    operationLogs.recordUserChanges(root, "批量授权", "分配课程角色", List.of(aliceEntity, bobEntity));
    operationLogs.recordGlobalChange(root, "修改角色", "全局配置", "ROLE", 1, "角色");
    aliceEntity.setDepartmentId(second.getId());
    users.flush();
    SessionUser manager =
        new SessionUser(3, "manager", "部门管理员", first.getId(), "DEPARTMENT_ADMIN");
    PageVO<OperationLogVO> visible = queryLogs.list(manager, 1, 20, "");
    assertThat(visible.items()).hasSize(1);
    assertThat(visible.items().getFirst().targetNames()).containsExactly("甲员工");
    assertThat(queryLogs.list(manager, 1, 20, "乙员工").total()).isZero();
  }

  /** 创建启用的普通用户（无管理身份），返回对应的会话用户。 */
  private SessionUser user(String name, Long dept) {
    UserAccount u = new UserAccount();
    u.setUsername(name);
    u.setName(name);
    u.setPasswordHash("hash");
    u.setStatus("ACTIVE");
    u.setDepartmentId(dept);
    users.save(u);
    return new SessionUser(u.getId(), u.getUsername(), u.getName(), dept, "NONE");
  }

  /** 创建课程应用及其下的菜单权限 course:view。 */
  private Permission permission() {
    BusinessApplication app = new BusinessApplication();
    app.setName("课程");
    app.setCode("course");
    apps.save(app);
    Permission p = new Permission();
    p.setApplicationId(app.getId());
    p.setName("课程");
    p.setCode("course:view");
    p.setType("MENU");
    return permissions.save(p);
  }

  /** 构造不继承父角色的角色配置请求。 */
  private RoleDTO role(String name, List<Long> ids) {
    RoleDTO r = new RoleDTO();
    r.setName(name);
    r.setPermissionIds(ids);
    r.setInheritedRoleIds(List.of());
    return r;
  }
}
