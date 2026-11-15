package com.rbac.rbac0;
import com.rbac.rbac0.service.ApprovalRules;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class ApprovalRulesTest {
    @Test void necessaryMenuAndAlreadyOwnedRightsAreNotExtraPrivileges() {
        assertThat(ApprovalRules.extraPermissions(Set.of(1L,2L,3L),Set.of(3L),2L,1L)).isEmpty();
    }
    @Test void unrelatedRightsMakeRoleUnsuitableForApproval() {
        assertThat(ApprovalRules.extraPermissions(Set.of(1L,2L,4L),Set.of(),2L,1L)).containsExactly(4L);
    }
}
