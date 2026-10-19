package com.adgn.scopefixture;

import com.adgn.backend.scope.service.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ScopeCoveragePolicyTest {
    private final ScopeCoveragePolicy policy=new ScopeCoveragePolicy();
    private ManagementContext scoped(String company,String department,String... systems){return new ManagementContext("account","appointment",2L,"scope",3L,company,department,Set.of(systems));}
    @Test void oneAppointmentMustCoverEverySystemInTheExactCompanyAndDepartment(){
        var scope=scoped("company","department","hr","oa");
        assertThat(policy.covers(scope,"company","department",Set.of("hr","oa"))).isTrue();
        assertThat(policy.covers(scope,"company","department",Set.of("hr"))).isTrue();
        assertThat(policy.covers(scope,"company","department",Set.of("crm"))).isFalse();
        assertThat(policy.covers(scope,"other","department",Set.of("hr"))).isFalse();
        assertThat(policy.covers(scope,"company","other",Set.of("hr"))).isFalse();
        assertThat(policy.covers(scope,"company",null,Set.of("hr"))).isFalse();
    }
    @Test void twoPartialAppointmentsCannotCoverOneCombinedOperation(){
        assertThat(policy.covers(scoped("c","d","hr"),"c","d",Set.of("hr","oa"))).isFalse();
        assertThat(policy.covers(scoped("c","d","oa"),"c","d",Set.of("hr","oa"))).isFalse();
    }
    @Test void emptySystemScopeDoesNotGrantAnySystemButCanCoverAnOrganizationOnlyTarget(){
        var scope=scoped("c","d");
        assertThat(policy.covers(scope,"c","d",Set.of())).isTrue();
        assertThat(policy.covers(scope,"c","d",Set.of("hr"))).isFalse();
    }
    @Test void globalContextNeedsNoAppointmentAndCanCoverCompanySharedTargets(){
        var global=new ManagementContext("super",null,null,null,null,null,null,Set.of());
        assertThat(policy.covers(global,"c",null,Set.of("hr","oa"))).isTrue();
        assertThat(policy.covers(global,"other","any",Set.of())).isTrue();
        assertThat(policy.covers(global,null,null,Set.of("hr"))).isFalse();
        assertThat(policy.covers(global,"c",null,null)).isFalse();
    }
}
