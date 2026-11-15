package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rbac.rbac1.dto.RequestDtos.RoleCreationReview;
import com.rbac.rbac1.dto.SessionDtos.SessionUser;
import com.rbac.rbac1.service.ManagementFailure;
import org.junit.jupiter.api.Test;
import java.util.*;

class InheritedRoleCreationRequestsTest extends InheritanceTestSupport {
  final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
  SessionUser manager() {
    var u=user("manager");
    return new SessionUser(u.id(),u.username(),u.name(),null,"DEPARTMENT_ADMIN");
  }
  @Test
  void applicationKeepsOriginalParentSnapshotWhileApprovalCanChooseAnotherParent() {
    var app=app("course"); var menu=permission(app,"course:view",null);
    var button=permission(app,"course:review",menu);
    var parent=role("原父角色",menu.id); var other=role("替换父角色",menu.id);
    var request=proposals.submit(manager(),input("审核助理",List.of(button.id),List.of(parent.id)));
    var before=json.valueToTree(proposals.get(root,request.id()));
    assertThat(before.path("inheritedRoles").get(0).path("name").asText()).isEqualTo("原父角色");
    assertThat(before.path("effectivePermissions").size()).isEqualTo(2);
    assertThat(before.path("effectivePermissions").get(1).path("sourceRoles").size()).isZero();
    service.update(root,parent.id,input("改名后",List.of(menu.id),List.of()));
    var approved=proposals.review(root,request.id(),new RoleCreationReview("APPROVE",
        input("调整后的审核助理",List.of(button.id),List.of(other.id))));
    assertThat(approved.status()).isEqualTo("APPROVED");
    var created=service.get(root,approved.createdRoleId());
    assertThat(created.inheritedRoles()).extracting(p -> p.id()).containsExactly(other.id);
    assertThat(assignments.findAll()).isEmpty();
    var history=json.valueToTree(proposals.get(root,request.id()));
    assertThat(history.path("inheritedRoles").get(0).path("name").asText()).isEqualTo("原父角色");
    assertThat(history.path("name").asText()).isEqualTo("审核助理");
  }
  @Test
  void deletedParentDoesNotBlockHistoryAndMustBeReplacedBeforeApproval() {
    var app=app("course"); var menu=permission(app,"course:view",null);
    var parent=role("待删除",menu.id);
    var request=proposals.submit(manager(),input("申请角色",List.of(),List.of(parent.id)));
    service.delete(root,parent.id);
    var detail=json.valueToTree(proposals.get(root,request.id()));
    assertThat(detail.path("inheritedRoles").get(0).path("exists").asBoolean()).isFalse();
    assertThat(detail.path("effectivePermissions").isNull()).isTrue();
    assertThatThrownBy(() -> proposals.review(root,request.id(),new RoleCreationReview("APPROVE",
        input("申请角色",List.of(),List.of(parent.id))))).isInstanceOf(ManagementFailure.class);
    assertThat(proposalRows.findById(request.id()).orElseThrow().status).isEqualTo("PENDING");
  }
  @Test
  void latestParentGrantProducesBothInPreviewButCannotCreateDuplicateDirectGrant() {
    var app=app("course"); var menu=permission(app,"course:view",null);
    var parent=role("父");
    var request=proposals.submit(manager(),input("申请角色",List.of(menu.id),List.of(parent.id)));
    service.update(root,parent.id,input("父",List.of(menu.id),List.of()));
    var detail=json.valueToTree(proposals.get(root,request.id()));
    assertThat(detail.path("effectivePermissions").get(0).path("acquisition").asText()).isEqualTo("BOTH");
    assertThatThrownBy(() -> proposals.review(root,request.id(),new RoleCreationReview("APPROVE",
        input("申请角色",List.of(menu.id),List.of(parent.id))))).isInstanceOf(ManagementFailure.class);
    assertThat(proposalRows.findById(request.id()).orElseThrow().status).isEqualTo("PENDING");
    assertThat(roles.findAll()).extracting(r -> r.name).doesNotContain("申请角色");
  }
}
