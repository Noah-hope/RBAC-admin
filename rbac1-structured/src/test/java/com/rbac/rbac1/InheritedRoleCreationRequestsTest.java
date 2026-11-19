package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbac.rbac1.dto.RoleCreationReviewDTO;
import com.rbac.rbac1.entity.BusinessApplication;
import com.rbac.rbac1.entity.Permission;
import com.rbac.rbac1.entity.Role;
import com.rbac.rbac1.service.ManagementFailure;
import com.rbac.rbac1.service.SessionUser;
import com.rbac.rbac1.vo.RoleCreationRequestVO;
import com.rbac.rbac1.vo.RoleDetailsVO;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 带父角色的角色创建申请测试：申请保留父角色快照，审批时按父角色最新配置重新校验，
 * 失败时申请仍为待审核。
 */
class InheritedRoleCreationRequestsTest extends InheritanceTestSupport {

  /** 把申请详情转成 JSON，按前端看到的字段断言。 */
  final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

  /** 创建一名部门管理员作为申请人。 */
  SessionUser manager() {
    SessionUser u = user("manager");
    return new SessionUser(u.id(), u.username(), u.name(), null, "DEPARTMENT_ADMIN");
  }

  @Test
  @DisplayName("验收11：申请详情展示父角色和有效权限，审批时可改选父角色，申请记录保留原父角色名称且不自动分配角色")
  void applicationKeepsOriginalParentSnapshotWhileApprovalCanChooseAnotherParent() {
    BusinessApplication app = app("course");
    Permission menu = permission(app, "course:view", null);
    Permission button = permission(app, "course:review", menu);
    Role parent = role("原父角色", menu.getId());
    Role other = role("替换父角色", menu.getId());
    RoleCreationRequestVO request =
        proposals.submit(
            manager(), input("审核助理", List.of(button.getId()), List.of(parent.getId())));
    JsonNode before = json.valueToTree(proposals.get(root, request.id()));
    assertThat(before.path("inheritedRoles").get(0).path("name").asText()).isEqualTo("原父角色");
    assertThat(before.path("effectivePermissions").size()).isEqualTo(2);
    assertThat(before.path("effectivePermissions").get(1).path("sourceRoles").size()).isZero();
    service.update(root, parent.getId(), input("改名后", List.of(menu.getId()), List.of()));
    RoleCreationRequestVO approved =
        proposals.review(
            root,
            request.id(),
            new RoleCreationReviewDTO(
                "APPROVE", input("调整后的审核助理", List.of(button.getId()), List.of(other.getId()))));
    assertThat(approved.status()).isEqualTo("APPROVED");
    RoleDetailsVO created = service.get(root, approved.createdRoleId());
    assertThat(created.inheritedRoles()).extracting(p -> p.id()).containsExactly(other.getId());
    assertThat(assignments.findAll()).isEmpty();
    JsonNode history = json.valueToTree(proposals.get(root, request.id()));
    assertThat(history.path("inheritedRoles").get(0).path("name").asText()).isEqualTo("原父角色");
    assertThat(history.path("name").asText()).isEqualTo("审核助理");
  }

  @Test
  @DisplayName("验收12：父角色删除后申请详情仍可查看，审批前必须更换父角色，失败时申请仍为待审核")
  void deletedParentDoesNotBlockHistoryAndMustBeReplacedBeforeApproval() {
    BusinessApplication app = app("course");
    Permission menu = permission(app, "course:view", null);
    Role parent = role("待删除", menu.getId());
    RoleCreationRequestVO request =
        proposals.submit(manager(), input("申请角色", List.of(), List.of(parent.getId())));
    service.delete(root, parent.getId());
    JsonNode detail = json.valueToTree(proposals.get(root, request.id()));
    assertThat(detail.path("inheritedRoles").get(0).path("exists").asBoolean()).isFalse();
    assertThat(detail.path("effectivePermissions").isNull()).isTrue();
    assertThatThrownBy(
            () ->
                proposals.review(
                    root,
                    request.id(),
                    new RoleCreationReviewDTO(
                        "APPROVE", input("申请角色", List.of(), List.of(parent.getId())))))
        .isInstanceOf(ManagementFailure.class);
    assertThat(proposalRows.findById(request.id()).orElseThrow().getStatus()).isEqualTo("PENDING");
  }

  @Test
  @DisplayName("验收12：审批时按父角色最新配置重新校验，与继承重复的直接配置使创建失败，申请仍为待审核")
  void latestParentGrantProducesBothInPreviewButCannotCreateDuplicateDirectGrant() {
    BusinessApplication app = app("course");
    Permission menu = permission(app, "course:view", null);
    Role parent = role("父");
    RoleCreationRequestVO request =
        proposals.submit(
            manager(), input("申请角色", List.of(menu.getId()), List.of(parent.getId())));
    service.update(root, parent.getId(), input("父", List.of(menu.getId()), List.of()));
    JsonNode detail = json.valueToTree(proposals.get(root, request.id()));
    assertThat(detail.path("effectivePermissions").get(0).path("acquisition").asText())
        .isEqualTo("BOTH");
    assertThatThrownBy(
            () ->
                proposals.review(
                    root,
                    request.id(),
                    new RoleCreationReviewDTO(
                        "APPROVE", input("申请角色", List.of(menu.getId()), List.of(parent.getId())))))
        .isInstanceOf(ManagementFailure.class);
    assertThat(proposalRows.findById(request.id()).orElseThrow().getStatus()).isEqualTo("PENDING");
    assertThat(roles.findAll()).extracting(r -> r.getName()).doesNotContain("申请角色");
  }
}
