package com.adgn.catalogfixture;

import com.adgn.backend.identity.repository.IdentityRepository;
import com.adgn.backend.query.management.ManagementAccess;
import com.adgn.backend.query.preview.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ImpactPolicyTest {
    @Test void hiddenRoleConfigurationChangesNeverDiscloseTheForeignRole() {
        var result=policy.calculate(new ImpactPolicy.Facts(facts()),change(Map.of("role-B",Set.of()),Set.of()),viewer());
        assertThat(result.impact().get("visibleRoleCount")).isEqualTo(0L);
        assertThat(result.impact().get("hasUndisclosedImpact")).isEqualTo(true);
        assertThat(json.writeValueAsString(result)).doesNotContain("role-B","grant-B","source-B");
    }

    @Test void explicitRoleGrantAndSourceTargetsAreIndependentlyCountedAndDeduplicated() {
        var targets=new ArrayList<tools.jackson.databind.JsonNode>();
        for(String type:List.of("ROLE","GRANT","SOURCE")){
            var target=json.createObjectNode().put("targetType",type).put("targetId",type+"-1");targets.add(target);targets.add(target);
        }
        var change=new ImpactPolicy.Change(Set.of(),Map.of(),Map.of(),Map.of(),Map.of(),Set.of(),Set.of(),Set.of(),Set.of(),List.of(),Set.of(),targets);
        var global=new ManagementAccess.Authority(new IdentityRepository.Account("admin",null,"unused",true,true,false,1,1,true),null,null,null,Set.of());
        var result=policy.calculate(new ImpactPolicy.Facts(facts()),change,global);
        assertThat(result.impact().get("visibleRoleCount")).isEqualTo(1L);
        assertThat(result.impact().get("visibleGrantCount")).isEqualTo(1L);
        assertThat(result.impact().get("visibleSourceCount")).isEqualTo(1L);
        assertThat(result.targets()).hasSize(3);
    }

    @Test void appointmentImpactIsVisibleOnlyToGlobalAdministrator() {
        var rows=facts();rows.put("appointments",List.of(row("id","appointment-X","company_id","company","department_id","B","appointment_version",7L)));
        var change=new ImpactPolicy.Change(Set.of(),Map.of(),Map.of(),Map.of(),Map.of(),Set.of(),Set.of(),Set.of(),Set.of("appointment-X"),List.of(),Set.of(),List.of());
        var hidden=policy.calculate(new ImpactPolicy.Facts(rows),change,viewer());
        assertThat(hidden.impact().get("hasUndisclosedImpact")).isEqualTo(true);
        assertThat(hidden.impact().get("visibleAppointmentCount")).isEqualTo(0L);
        assertThat(json.writeValueAsString(hidden)).doesNotContain("appointment-X");
        var global=new ManagementAccess.Authority(new IdentityRepository.Account("admin",null,"unused",true,true,false,1,1,true),null,null,null,Set.of());
        var visible=policy.calculate(new ImpactPolicy.Facts(rows),change,global);
        assertThat(visible.impact().get("visibleAppointmentCount")).isEqualTo(1L);
        assertThat(visible.targets().getFirst().path("targetId").stringValue()).isEqualTo("appointment-X");
    }
    private final JsonMapper json=JsonMapper.builder().build();
    private final ImpactPolicy policy=new ImpactPolicy(json);
    private final ImpactPolicy.Source a=new ImpactPolicy.Source("employee","company","A","system");
    private static Map<String,Object> row(Object... fields){var row=new LinkedHashMap<String,Object>();for(int i=0;i<fields.length;i+=2)row.put((String)fields[i],fields[i+1]);return row;}
    private Map<String,List<Map<String,Object>>> facts(){
        var facts=new TreeMap<String,List<Map<String,Object>>>();
        facts.put("systems",List.of(row("id","system","enabled",true)));
        facts.put("permissions",List.of(row("id","access","system_id","system","permission_type","SYSTEM_ACCESS","function_id",null,"enabled",true)));
        facts.put("roles",List.of(row("id","role-A","company_id","company","department_id","A","system_id","system","state","EFFECTIVE","configuration_version",1L),row("id","role-B","company_id","company","department_id","B","system_id","system","state","EFFECTIVE","configuration_version",1L)));
        facts.put("rolePermissions",List.of(row("role_id","role-A","permission_id","access"),row("role_id","role-B","permission_id","access")));
        facts.put("sources",List.of(row("id","source-A","employee_id","employee","company_id","company","department_id","A","system_id","system","source_epoch",0L),row("id","source-B","employee_id","employee","company_id","company","department_id","B","system_id","system","source_epoch",0L)));
        facts.put("baselines",List.of(row("id","baseline-A","source_id","source-A","role_id","role-A","relation_version",1L),row("id","baseline-B","source_id","source-B","role_id","role-B","relation_version",1L)));
        facts.put("grants",List.of(row("id","grant-A","baseline_id","baseline-A","status","ACTIVE","source_epoch",0L,"relation_version",1L),row("id","grant-B","baseline_id","baseline-B","status","ACTIVE","source_epoch",0L,"relation_version",1L)));
        facts.put("employees",List.of(row("id","employee","status","ACTIVE")));
        facts.put("employments",List.of(row("employee_id","employee","company_id","company","department_id","A","status","ACTIVE"),row("employee_id","employee","company_id","company","department_id","B","status","ACTIVE")));
        return facts;
    }
    private ManagementAccess.Authority viewer(){return new ManagementAccess.Authority(new IdentityRepository.Account("admin","employee","unused",true,false,false,1,1,true),"scope","company","A",Set.of("system"));}
    private ImpactPolicy.Change change(Map<String,Set<String>> configurations,Set<ImpactPolicy.Source> closed){return new ImpactPolicy.Change(Set.of("system"),Map.of(),Map.of(),Map.of(),configurations,closed,Set.of(),Set.of(),Set.of(),List.of(),Set.of(),List.of());}
    @Test void closingVisibleSourceStillComputesOtherDepartmentAccessWithoutDisclosingIt(){
        var result=policy.calculate(new ImpactPolicy.Facts(facts()),change(Map.of(),Set.of(a)),viewer());
        assertThat(result.impact().get("visibleEmployeeCount")).isEqualTo(1);
        assertThat(result.impact().get("visibleRetainingAccessCount")).isEqualTo(1L);
        assertThat(result.impact().get("visibleGrantCount")).isEqualTo(1L);
        assertThat(result.impact().get("hasUndisclosedImpact")).isEqualTo(true);
        assertThat(result.employees().getFirst().path("canAccessAfter").booleanValue()).isTrue();
        assertThat(result.employees().getFirst().path("visibleGrantIds").toString()).isEqualTo("[\"grant-A\"]");
        assertThat(json.writeValueAsString(result)).doesNotContain("grant-B","source-B","role-B");
    }
    @Test void configurationChangeUsesTheWholeUnionNotOnlyVisibleSources(){
        var result=policy.calculate(new ImpactPolicy.Facts(facts()),change(Map.of("role-A",Set.of()),Set.of()),viewer());
        assertThat(result.impact().get("visibleRetainingAccessCount")).isEqualTo(1L);
        assertThat(result.impact().get("visibleRoleCount")).isEqualTo(1L);
        assertThat(result.employees().getFirst().path("canAccessAfter").booleanValue()).isTrue();
    }
    @Test void endedOrOldEpochSourcesCannotSupplyRemainingAccess(){
        var rows=facts();rows.put("grants",List.of(row("id","grant-A","baseline_id","baseline-A","status","ACTIVE","source_epoch",9L,"relation_version",1L),row("id","grant-B","baseline_id","baseline-B","status","REVOKED","source_epoch",0L,"relation_version",2L)));
        var result=policy.calculate(new ImpactPolicy.Facts(rows),change(Map.of("role-A",Set.of()),Set.of()),viewer());
        assertThat(result.employees().getFirst().path("canAccessBefore").booleanValue()).isFalse();
        assertThat(result.employees().getFirst().path("canAccessAfter").booleanValue()).isFalse();
    }
    @Test void mixedPendingRequestNeverLeaksItsIdOrCount(){
        var rows=facts();rows.put("pendingRequests",List.of(row("id","mixed-request","company_id","company","status_version",3L)));
        rows.put("pendingTargets",List.of(row("request_id","mixed-request","role_id","role-A","company_id","company","department_id","A","system_id","system","employee_id",null),row("request_id","mixed-request","role_id","role-B","company_id","company","department_id","B","system_id","system","employee_id",null)));
        var result=policy.calculate(new ImpactPolicy.Facts(rows),change(Map.of("role-A",Set.of()),Set.of()),viewer());
        assertThat(result.impact().get("visiblePendingRequestCount")).isEqualTo(0L);
        assertThat(result.impact().get("hasUndisclosedImpact")).isEqualTo(true);
        assertThat(json.writeValueAsString(result)).doesNotContain("mixed-request");
    }
    @Test void unrelatedConfigurationRequestWithNoEmployeeTargetDoesNotBreakSourcePreview(){
        var rows=facts();rows.put("pendingRequests",List.of(row("id","unrelated","company_id","company","status_version",1L)));
        rows.put("pendingTargets",List.of(row("request_id","unrelated","role_id","other-role","company_id","company","department_id","A","system_id","system","employee_id",null)));
        assertThatCode(()->policy.calculate(new ImpactPolicy.Facts(rows),change(Map.of(),Set.of(a)),viewer())).doesNotThrowAnyException();
        var result=policy.calculate(new ImpactPolicy.Facts(rows),change(Map.of(),Set.of(a)),viewer());
        assertThat(result.impact().get("visiblePendingRequestCount")).isEqualTo(0L);
        assertThat(result.employees()).hasSize(1);
    }
    @Test void sourceClosureIncludesPendingRequestsUsingThatSource(){
        var rows=facts();rows.put("pendingRequests",List.of(row("id","pending-source","company_id","company","status_version",1L)));
        rows.put("pendingTargets",List.of(row("request_id","pending-source","role_id","role-A","company_id","company","department_id","A","system_id","system","employee_id","employee","source_id","source-A")));
        var result=policy.calculate(new ImpactPolicy.Facts(rows),change(Map.of(),Set.of(a)),viewer());
        assertThat(result.impact().get("visiblePendingRequestCount")).isEqualTo(1L);
        assertThat(json.writeValueAsString(result.targets())).contains("pending-source");
    }
    @Test void pendingConfigurationCanBeAffectedEvenIfCurrentRoleDoesNotSelectChangedPermission(){
        var rows=facts();rows.put("pendingRequests",List.of(row("id","pending-config","company_id","company","status_version",1L,"content","{\"permissionIds\":[\"future-permission\"]}")));
        rows.put("pendingTargets",List.of(row("request_id","pending-config","role_id","role-A","company_id","company","department_id","A","system_id","system","employee_id",null)));
        var change=new ImpactPolicy.Change(Set.of("system"),Map.of(),Map.of(),Map.of("future-permission",new ImpactPolicy.PermissionState(false,true)),Map.of(),Set.of(),Set.of(),Set.of(),Set.of(),List.of(),Set.of("future-permission"),List.of());
        var result=policy.calculate(new ImpactPolicy.Facts(rows),change,viewer());
        assertThat(result.impact().get("visiblePendingRequestCount")).isEqualTo(1L);
    }
    @Test void explicitAppointmentTargetsAreCountedOnceForScopePreviewConsumers(){
        var appointment=json.createObjectNode().put("targetType","APPOINTMENT").put("targetId","new-appointment").putNull("source").put("companyId","company").put("departmentId","A").putNull("systemId").putNull("targetVersion").put("effect","ENABLE").put("summary","新增任命");
        var change=new ImpactPolicy.Change(Set.of("system"),Map.of(),Map.of(),Map.of(),Map.of(),Set.of(),Set.of(),Set.of(),Set.of(),List.of(),Set.of(),List.of(appointment,appointment));
        var global=new ManagementAccess.Authority(new IdentityRepository.Account("admin",null,"unused",true,true,false,1,1,true),null,null,null,Set.of());
        var result=policy.calculate(new ImpactPolicy.Facts(facts()),change,global);
        assertThat(result.impact().get("visibleAppointmentCount")).isEqualTo(1L);assertThat(result.targets()).hasSize(1);
    }
}
