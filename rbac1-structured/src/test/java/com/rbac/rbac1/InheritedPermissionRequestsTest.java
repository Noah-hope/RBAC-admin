package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rbac.rbac1.dto.BatchRoleChangeDTO;
import com.rbac.rbac1.dto.PermissionRequestDTO;
import com.rbac.rbac1.dto.PermissionReviewDTO;
import com.rbac.rbac1.entity.BusinessApplication;
import com.rbac.rbac1.entity.Department;
import com.rbac.rbac1.entity.Permission;
import com.rbac.rbac1.entity.Role;
import com.rbac.rbac1.entity.UserRole;
import com.rbac.rbac1.service.ManagementFailure;
import com.rbac.rbac1.service.SessionUser;
import com.rbac.rbac1.vo.ApprovalOptionVO;
import com.rbac.rbac1.vo.PermissionRequestVO;
import com.rbac.rbac1.vo.RequestGroupVO;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 继承权限参与权限申请的测试：申请目录、候选角色和无关权限判断都包含继承权限，
 * 授权或继承变化后受影响的待审核申请转为已满足。
 */
class InheritedPermissionRequestsTest extends InheritanceTestSupport {

  @Test
  @DisplayName("验收9：申请目录排除已通过继承获得的权限，菜单通过继承获得后可以申请其下的按钮")
  void inheritedMenuHidesOwnedPermissionsAndAllowsRequestingItsButtons() {
    BusinessApplication app = app("course");
    Permission menu = permission(app, "course:view", null);
    Permission button = permission(app, "course:review", menu);
    Role base = role("菜单", menu.getId());
    Role child = role("子");
    inherits(child, base);
    SessionUser user = user("teacher");
    assign(user, child);
    RequestGroupVO catalog =
        requests.requestable(user).applications().getFirst().groups().getFirst();
    assertThat(catalog.permissions()).extracting(p -> p.id()).containsExactly(button.getId());
    assertThat(requests.submit(user, new PermissionRequestDTO(button.getId(), "审核课程")).status())
        .isEqualTo("PENDING");
    assertThatThrownBy(() -> requests.submit(user, new PermissionRequestDTO(menu.getId(), "已继承")))
        .isInstanceOf(ManagementFailure.class);
  }

  @Test
  @DisplayName("验收9：候选角色按包含继承的有效权限判断，所有应用的权限一并评估，批准后只分配所选角色")
  void candidatesUseAllApplicationsAndApprovalAssignsOnlyTheChosenRole() {
    BusinessApplication app = app("course");
    Permission menu = permission(app, "course:view", null);
    Permission button = permission(app, "course:review", menu);
    Permission extra = permission(app("other"), "other:view", null);
    Role base = role("菜单", menu.getId());
    Role reviewer = role("审核来源", button.getId());
    inherits(reviewer, base);
    Role minimal = role("最小候选");
    inherits(minimal, reviewer);
    Role excessive = role("附带其他应用权限", extra.getId());
    inherits(excessive, reviewer);
    SessionUser user = user("teacher");
    assign(user, base);
    PermissionRequestVO request =
        requests.submit(user, new PermissionRequestDTO(button.getId(), "审核课程"));
    List<ApprovalOptionVO> options = requests.get(root, request.id()).approvalRoles();
    assertThat(options)
        .anySatisfy(
            o -> {
              assertThat(o.role().id()).isEqualTo(minimal.getId());
              assertThat(o.selectable()).isTrue();
            });
    ApprovalOptionVO bad =
        options.stream()
            .filter(o -> o.role().id() == excessive.getId())
            .findFirst()
            .orElseThrow();
    assertThat(bad.selectable()).isFalse();
    assertThat(bad.extraPermissionIds()).containsExactly(extra.getId());
    PermissionRequestVO result =
        requests.review(root, request.id(), new PermissionReviewDTO("APPROVE", minimal.getId()));
    assertThat(result.status()).isEqualTo("APPROVED");
    assertThat(assignments.findAll())
        .extracting(a -> a.getRoleId())
        .containsExactlyInAnyOrder(base.getId(), minimal.getId());
  }

  @Test
  @DisplayName("验收10：父角色新增授权后受影响用户的待审核申请转为已满足，其他历史状态不变")
  void parentGrantSettlesAffectedPendingRequestsAndRetainsEndedHistory() {
    BusinessApplication app = app("course");
    Permission menu = permission(app, "course:view", null);
    Permission button = permission(app, "course:review", menu);
    Permission other = permission(app, "course:edit", menu);
    Role parent = role("父", menu.getId());
    Role child = role("子");
    inherits(child, parent);
    SessionUser user = user("teacher");
    assign(user, child);
    PermissionRequestVO withdrawn =
        requests.submit(user, new PermissionRequestDTO(button.getId(), "已撤回"));
    requests.withdraw(user, withdrawn.id());
    PermissionRequestVO pending =
        requests.submit(user, new PermissionRequestDTO(button.getId(), "待满足"));
    PermissionRequestVO unmatched =
        requests.submit(user, new PermissionRequestDTO(other.getId(), "仍待审"));
    SessionUser unrelated = user("unrelated");
    PermissionRequestVO outside =
        requests.submit(unrelated, new PermissionRequestDTO(menu.getId(), "不受影响"));
    service.update(
        root, parent.getId(), input("父", List.of(menu.getId(), button.getId()), List.of()));
    assertThat(requestRows.findById(pending.id()).orElseThrow().getStatus()).isEqualTo("SATISFIED");
    assertThat(requestRows.findById(withdrawn.id()).orElseThrow().getStatus())
        .isEqualTo("WITHDRAWN");
    assertThat(requestRows.findById(unmatched.id()).orElseThrow().getStatus())
        .isEqualTo("PENDING");
    assertThat(requestRows.findById(outside.id()).orElseThrow().getStatus()).isEqualTo("PENDING");
    assertThat(requestRows.findById(pending.id()).orElseThrow().getReviewerUserId()).isNull();
  }

  @Test
  @DisplayName("验收9：审批时按最新继承权限重新判断无关权限，候选角色已带无关权限时拒绝批准")
  void approvalRechecksLatestInheritedRightsBeforeGranting() {
    BusinessApplication app = app("course");
    Permission menu = permission(app, "course:view", null);
    Permission button = permission(app, "course:review", menu);
    Permission extra = permission(app("other"), "other:view", null);
    Role base = role("菜单", menu.getId());
    Role reviewer = role("来源", button.getId());
    inherits(reviewer, base);
    Role candidate = role("候选");
    inherits(candidate, reviewer);
    SessionUser user = user("teacher");
    assign(user, base);
    PermissionRequestVO request =
        requests.submit(user, new PermissionRequestDTO(button.getId(), "待审核"));
    assertThat(requests.get(root, request.id()).approvalRoles())
        .anySatisfy(
            o -> {
              assertThat(o.role().id()).isEqualTo(candidate.getId());
              assertThat(o.selectable()).isTrue();
            });
    service.update(
        root,
        reviewer.getId(),
        input("来源", List.of(button.getId(), extra.getId()), List.of(base.getId())));
    assertThatThrownBy(
            () ->
                requests.review(
                    root, request.id(), new PermissionReviewDTO("APPROVE", candidate.getId())))
        .isInstanceOf(ManagementFailure.class);
    assertThat(assignments.existsById(new UserRole.Key(user.id(), candidate.getId()))).isFalse();
    assertThat(requestRows.findById(request.id()).orElseThrow().getStatus()).isEqualTo("PENDING");
  }

  @Test
  @DisplayName("验收10：单个和批量分配角色后只把已满足的待审核申请转为已满足，且不分配父角色")
  void singleAndBatchAssignmentsSettleOnlyPendingRequestsWithoutAssigningAncestors() {
    BusinessApplication app = app("course");
    Permission menu = permission(app, "course:view", null);
    Department department = new Department();
    department.setName("学院");
    departments.save(department);
    Role parent = role("权限来源", menu.getId());
    Role child = role("实际分配角色");
    inherits(child, parent);
    SessionUser first = user("first");
    SessionUser second = user("second");
    users.findById(first.id()).orElseThrow().setDepartmentId(department.getId());
    users.findById(second.id()).orElseThrow().setDepartmentId(department.getId());
    PermissionRequestVO one =
        requests.submit(first, new PermissionRequestDTO(menu.getId(), "单人分配"));
    PermissionRequestVO two =
        requests.submit(second, new PermissionRequestDTO(menu.getId(), "批量分配"));
    authorizations.replace(root, first.id(), List.of(child.getId()));
    assertThat(requestRows.findById(one.id()).orElseThrow().getStatus()).isEqualTo("SATISFIED");
    assertThat(requestRows.findById(two.id()).orElseThrow().getStatus()).isEqualTo("PENDING");
    authorizations.batch(
        root, new BatchRoleChangeDTO("GRANT", List.of(second.id()), List.of(child.getId())));
    assertThat(requestRows.findById(two.id()).orElseThrow().getStatus()).isEqualTo("SATISFIED");
    assertThat(assignments.findAll()).extracting(a -> a.getRoleId()).containsOnly(child.getId());
    authorizations.batch(
        root, new BatchRoleChangeDTO("REVOKE", List.of(second.id()), List.of(child.getId())));
    assertThat(checks.checkPermission(second.id(), "course", "course:view").allowed()).isFalse();
    assertThat(requestRows.findById(two.id()).orElseThrow().getStatus()).isEqualTo("SATISFIED");
  }
}
