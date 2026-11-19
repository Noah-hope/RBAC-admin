package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.assertThat;

import com.rbac.rbac1.entity.Role;
import com.rbac.rbac1.entity.RoleCreationRequest;
import com.rbac.rbac1.entity.RoleCreationRequestInheritance;
import com.rbac.rbac1.entity.RoleInheritance;
import com.rbac.rbac1.service.SessionUser;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 继承相关表的持久化测试：角色继承和申请父角色快照都使用复合主键，
 * 清空持久化上下文后能各自按主键读回。
 */
class InheritancePersistenceTest extends InheritanceTestSupport {

  @Autowired
  EntityManager entityManager;

  @Test
  @DisplayName("验收12：角色继承与申请父角色快照按复合主键独立保存，快照保留申请时的父角色名称")
  void compositeKeysAndParentNameSnapshotsRoundTripIndependently() {
    Role parent = role("父角色");
    Role child = role("子角色");
    inherits(child, parent);
    SessionUser applicant = user("manager");
    RoleCreationRequest request = new RoleCreationRequest();
    request.setApplicantUserId(applicant.id());
    request.setRoleName("申请角色");
    request.setRoleDescription("");
    request.setStatus("PENDING");
    request.setCreatedAt(LocalDateTime.now());
    proposalRows.save(request);
    RoleCreationRequestInheritance snapshot = new RoleCreationRequestInheritance();
    snapshot.setRequestId(request.getId());
    snapshot.setInheritedRoleId(parent.getId());
    snapshot.setInheritedRoleName("申请时的父角色名称");
    proposalParents.save(snapshot);
    entityManager.flush();
    entityManager.clear();
    RoleInheritance stored =
        inheritance.findById(new RoleInheritance.Key(child.getId(), parent.getId())).orElseThrow();
    assertThat(stored.getRoleId()).isEqualTo(child.getId());
    assertThat(stored.getInheritedRoleId()).isEqualTo(parent.getId());
    assertThat(
            proposalParents
                .findById(new RoleCreationRequestInheritance.Key(request.getId(), parent.getId()))
                .orElseThrow()
                .getInheritedRoleName())
        .isEqualTo("申请时的父角色名称");
  }
}
