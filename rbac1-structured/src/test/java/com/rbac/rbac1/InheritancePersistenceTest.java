package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.*;

import com.rbac.rbac1.entity.*;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InheritancePersistenceTest extends InheritanceTestSupport {
  @Autowired EntityManager entityManager;

  @Test
  void compositeKeysAndParentNameSnapshotsRoundTripIndependently() {
    var parent = role("父角色");
    var child = role("子角色");
    inherits(child, parent);
    var applicant = user("manager");
    var request = new RoleCreationRequest();
    request.applicantUserId = applicant.id();
    request.roleName = "申请角色";
    request.roleDescription = "";
    request.status = "PENDING";
    request.createdAt = LocalDateTime.now();
    proposalRows.save(request);
    var snapshot = new RoleCreationRequestInheritance();
    snapshot.requestId = request.id;
    snapshot.inheritedRoleId = parent.id;
    snapshot.inheritedRoleName = "申请时的父角色名称";
    proposalParents.save(snapshot);
    entityManager.flush();
    entityManager.clear();
    var stored = inheritance.findById(new RoleInheritance.Key(child.id, parent.id)).orElseThrow();
    assertThat(stored.roleId).isEqualTo(child.id);
    assertThat(stored.inheritedRoleId).isEqualTo(parent.id);
    assertThat(
            proposalParents
                .findById(new RoleCreationRequestInheritance.Key(request.id, parent.id))
                .orElseThrow()
                .inheritedRoleName)
        .isEqualTo("申请时的父角色名称");
  }
}
