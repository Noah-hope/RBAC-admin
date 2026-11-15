package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.*;

import com.rbac.rbac1.dto.RequestDtos.*;
import com.rbac.rbac1.service.ManagementFailure;
import java.util.*;
import org.junit.jupiter.api.Test;

class InheritedPermissionRequestsTest extends InheritanceTestSupport {
  @Test
  void inheritedMenuHidesOwnedPermissionsAndAllowsRequestingItsButtons() {
    var app = app("course");
    var menu = permission(app, "course:view", null);
    var button = permission(app, "course:review", menu);
    var base = role("菜单", menu.id);
    var child = role("子");
    inherits(child, base);
    var user = user("teacher");
    assign(user, child);
    var catalog = requests.requestable(user).applications().getFirst().groups().getFirst();
    assertThat(catalog.permissions()).extracting(p -> p.id()).containsExactly(button.id);
    assertThat(requests.submit(user, new PermissionRequestInput(button.id, "审核课程")).status())
        .isEqualTo("PENDING");
    assertThatThrownBy(() -> requests.submit(user, new PermissionRequestInput(menu.id, "已继承")))
        .isInstanceOf(ManagementFailure.class);
  }

  @Test
  void candidatesUseAllApplicationsAndApprovalAssignsOnlyTheChosenRole() {
    var app = app("course");
    var menu = permission(app, "course:view", null);
    var button = permission(app, "course:review", menu);
    var extra = permission(app("other"), "other:view", null);
    var base = role("菜单", menu.id);
    var reviewer = role("审核来源", button.id);
    inherits(reviewer, base);
    var minimal = role("最小候选");
    inherits(minimal, reviewer);
    var excessive = role("附带其他应用权限", extra.id);
    inherits(excessive, reviewer);
    var user = user("teacher");
    assign(user, base);
    var request = requests.submit(user, new PermissionRequestInput(button.id, "审核课程"));
    var options = requests.get(root, request.id()).approvalRoles();
    assertThat(options)
        .anySatisfy(
            o -> {
              assertThat(o.role().id()).isEqualTo(minimal.id);
              assertThat(o.selectable()).isTrue();
            });
    var bad = options.stream().filter(o -> o.role().id() == excessive.id).findFirst().orElseThrow();
    assertThat(bad.selectable()).isFalse();
    assertThat(bad.extraPermissionIds()).containsExactly(extra.id);
    var result = requests.review(root, request.id(), new PermissionReview("APPROVE", minimal.id));
    assertThat(result.status()).isEqualTo("APPROVED");
    assertThat(assignments.findAll())
        .extracting(a -> a.roleId)
        .containsExactlyInAnyOrder(base.id, minimal.id);
  }

  @Test
  void parentGrantSettlesAffectedPendingRequestsAndRetainsEndedHistory() {
    var app = app("course");
    var menu = permission(app, "course:view", null);
    var button = permission(app, "course:review", menu);
    var other = permission(app, "course:edit", menu);
    var parent = role("父", menu.id);
    var child = role("子");
    inherits(child, parent);
    var user = user("teacher");
    assign(user, child);
    var withdrawn = requests.submit(user, new PermissionRequestInput(button.id, "已撤回"));
    requests.withdraw(user, withdrawn.id());
    var pending = requests.submit(user, new PermissionRequestInput(button.id, "待满足"));
    var unmatched = requests.submit(user, new PermissionRequestInput(other.id, "仍待审"));
    var unrelated = user("unrelated");
    var outside = requests.submit(unrelated, new PermissionRequestInput(menu.id, "不受影响"));
    service.update(root, parent.id, input("父", List.of(menu.id, button.id), List.of()));
    assertThat(requestRows.findById(pending.id()).orElseThrow().status).isEqualTo("SATISFIED");
    assertThat(requestRows.findById(withdrawn.id()).orElseThrow().status).isEqualTo("WITHDRAWN");
    assertThat(requestRows.findById(unmatched.id()).orElseThrow().status).isEqualTo("PENDING");
    assertThat(requestRows.findById(outside.id()).orElseThrow().status).isEqualTo("PENDING");
    assertThat(requestRows.findById(pending.id()).orElseThrow().reviewerUserId).isNull();
  }

  @Test
  void approvalRechecksLatestInheritedRightsBeforeGranting() {
    var app = app("course");
    var menu = permission(app, "course:view", null);
    var button = permission(app, "course:review", menu);
    var extra = permission(app("other"), "other:view", null);
    var base = role("菜单", menu.id);
    var reviewer = role("来源", button.id);
    inherits(reviewer, base);
    var candidate = role("候选");
    inherits(candidate, reviewer);
    var user = user("teacher");
    assign(user, base);
    var request = requests.submit(user, new PermissionRequestInput(button.id, "待审核"));
    assertThat(requests.get(root, request.id()).approvalRoles())
        .anySatisfy(
            o -> {
              assertThat(o.role().id()).isEqualTo(candidate.id);
              assertThat(o.selectable()).isTrue();
            });
    service.update(root, reviewer.id, input("来源", List.of(button.id, extra.id), List.of(base.id)));
    assertThatThrownBy(
            () ->
                requests.review(root, request.id(), new PermissionReview("APPROVE", candidate.id)))
        .isInstanceOf(ManagementFailure.class);
    assertThat(
            assignments.existsById(new com.rbac.rbac1.entity.UserRole.Key(user.id(), candidate.id)))
        .isFalse();
    assertThat(requestRows.findById(request.id()).orElseThrow().status).isEqualTo("PENDING");
  }

  @Test
  void singleAndBatchAssignmentsSettleOnlyPendingRequestsWithoutAssigningAncestors() {
    var app = app("course");
    var menu = permission(app, "course:view", null);
    var department = new com.rbac.rbac1.entity.Department();
    department.name = "学院";
    departments.save(department);
    var parent = role("权限来源", menu.id);
    var child = role("实际分配角色");
    inherits(child, parent);
    var first = user("first");
    var second = user("second");
    users.findById(first.id()).orElseThrow().departmentId = department.id;
    users.findById(second.id()).orElseThrow().departmentId = department.id;
    var one = requests.submit(first, new PermissionRequestInput(menu.id, "单人分配"));
    var two = requests.submit(second, new PermissionRequestInput(menu.id, "批量分配"));
    authorizations.replace(root, first.id(), List.of(child.id));
    assertThat(requestRows.findById(one.id()).orElseThrow().status).isEqualTo("SATISFIED");
    assertThat(requestRows.findById(two.id()).orElseThrow().status).isEqualTo("PENDING");
    authorizations.batch(
        root,
        new com.rbac.rbac1.dto.AuthorizationDtos.BatchRoleChange(
            "GRANT", List.of(second.id()), List.of(child.id)));
    assertThat(requestRows.findById(two.id()).orElseThrow().status).isEqualTo("SATISFIED");
    assertThat(assignments.findAll()).extracting(a -> a.roleId).containsOnly(child.id);
    authorizations.batch(
        root,
        new com.rbac.rbac1.dto.AuthorizationDtos.BatchRoleChange(
            "REVOKE", List.of(second.id()), List.of(child.id)));
    assertThat(checks.checkPermission(second.id(), "course", "course:view").allowed()).isFalse();
    assertThat(requestRows.findById(two.id()).orElseThrow().status).isEqualTo("SATISFIED");
  }
}
