package com.adgn.backend.lifecycle;

import com.adgn.backend.change.WorkflowStateWriter;
import com.adgn.backend.core.audit.*;
import com.adgn.backend.core.transaction.WriteContext;
import com.adgn.backend.grant.AffectedFacts;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.util.*;

/** Full internal selection. Response visibility must never trim the business invalidation set. */
public final class RequestInvalidator {
    public record Changes(AffectedFacts grants,Set<String> inactiveEmployees,Set<String> employmentChangedEmployees,Set<String> changedScopes,Map<String,Long> roleVersions) {
        public Changes {Objects.requireNonNull(grants);inactiveEmployees=Set.copyOf(inactiveEmployees);employmentChangedEmployees=Set.copyOf(employmentChangedEmployees);changedScopes=Set.copyOf(changedScopes);roleVersions=Map.copyOf(roleVersions);}
    }
    public record Invalidated(String requestId,String reasonCode,long beforeVersion,long afterVersion,String auditId) { }
    private final DataSource source;private final JdbcTemplate jdbc;private final WorkflowStateWriter states;private final AuditAppender audit;private final JsonMapper json;
    public RequestInvalidator(DataSource source,WorkflowStateWriter states,AuditAppender audit,JsonMapper json){this.source=source;this.jdbc=new JdbcTemplate(source);this.states=states;this.audit=audit;this.json=json;}
    public List<Invalidated> sources(WriteContext<?> c,AffectedFacts facts,String correlation,String reason){return invalidate(c,new Changes(facts,Set.of(),Set.of(),Set.of(),Map.of()),correlation,reason);}
    public List<Invalidated> invalidate(WriteContext<?> c,Changes changes,String correlation,String reason){
        c.requireActive(source);
        try{
            var facts=changes.grants();
            var grants=new HashSet<String>();for(var r:facts.relations())if(r.grantId()!=null)grants.add(r.grantId());
            var candidates=jdbc.queryForList("SELECT id,kind,status_version,company_id,submitted_by_account_id,authority_type,scope_id FROM change_request WHERE submitted_version IS NOT NULL AND state IN ('PENDING','APPROVED','EXECUTION_FAILED') ORDER BY id");
            var result=new ArrayList<Invalidated>();
            for(var request:candidates){
                String id=(String)request.get("id"),kind=(String)request.get("kind"),code=null;
                var targets=jdbc.queryForList("SELECT company_id,department_id,system_id,employee_id,grant_id,role_id,baseline_id,expected_configuration_version,expected_relation_version,expected_source_epoch FROM change_target WHERE request_id=? ORDER BY line_no",id);
                for(var target:targets){
                    if(kind.equals("ROLE_GRANT") && facts.sources().stream().anyMatch(s->{var key=s.after().key();var epoch=target.get("expected_source_epoch");
                        return key.employeeId().equals(target.get("employee_id"))&&key.companyId().equals(target.get("company_id"))&&key.departmentId().equals(target.get("department_id"))&&key.systemId().equals(target.get("system_id"))
                                &&epoch instanceof Number n&&n.longValue()<s.after().sourceEpoch();})){code="SOURCE_EPOCH_CHANGED";break;}
                    if(kind.equals("ROLE_REVOCATION")&&grants.contains(target.get("grant_id"))){code="RELATION_VERSION_CHANGED";break;}
                    if(target.get("employee_id")!=null&&changes.inactiveEmployees().contains(target.get("employee_id"))){code="EMPLOYEE_INACTIVE";break;}
                    if(kind.equals("ROLE_GRANT")){
                        if(facts.relations().stream().anyMatch(r->r.baselineId().equals(target.get("baseline_id"))&&target.get("expected_relation_version") instanceof Number n&&n.longValue()<r.afterVersion())){code="RELATION_VERSION_CHANGED";break;}
                        var version=changes.roleVersions().get(target.get("role_id"));
                        if(version!=null&&target.get("expected_configuration_version") instanceof Number n&&n.longValue()!=version){code="ROLE_CONFIGURATION_CHANGED";break;}
                        if(changes.employmentChangedEmployees().contains(target.get("employee_id"))&&!applicable(target)){code="NO_APPLICABLE_EMPLOYMENT";break;}
                    }
                }
                if(code==null&&"SCOPE".equals(request.get("authority_type"))&&changes.changedScopes().contains(request.get("scope_id"))&&!covered(request,targets))code="SCOPE_NO_LONGER_COVERS";
                if(code==null)continue;
                long version=((Number)request.get("status_version")).longValue();
                if(states.invalidate(c,id,version,code)){
                    var event=audit.append(c,new AuditRecord("CHANGE_REQUEST_INVALIDATED",id,UUID.fromString(id),UUID.fromString((String)request.get("submitted_by_account_id")),
                            new AuditRecord.Scope(AuditRecord.Visibility.GLOBAL,UUID.fromString((String)request.get("company_id")),null,null),"SUCCEEDED",correlation,reason,
                            json.writeValueAsString(Map.of("schemaVersion",1,"requestId",id,"beforeVersion",version,"afterVersion",version+1,"failureCode",code,"targets",targets)),List.of(new AuditRecord.Subject("REQUEST",id))));
                    result.add(new Invalidated(id,code,version,version+1,event.toString()));
                }
            }
            return List.copyOf(result);
        }catch(RuntimeException failure){c.markFailed(failure);throw failure;}
    }
    private boolean applicable(Map<String,Object> target){
        var roles=jdbc.queryForList("SELECT company_id,department_id,system_id FROM business_role WHERE id=?",target.get("role_id"));if(roles.size()!=1)return false;
        var role=roles.getFirst();if(!Objects.equals(role.get("company_id"),target.get("company_id"))||!Objects.equals(role.get("system_id"),target.get("system_id")))return false;
        return employed(target.get("employee_id"),target.get("company_id"),target.get("department_id"))&&(role.get("department_id")==null||employed(target.get("employee_id"),target.get("company_id"),role.get("department_id")));
    }
    private boolean employed(Object employee,Object company,Object department){return jdbc.queryForObject("SELECT COUNT(*) FROM employment WHERE employee_id=? AND company_id=? AND department_id=? AND status='ACTIVE'",Long.class,employee,company,department)>0;}
    private boolean covered(Map<String,Object> request,List<Map<String,Object>> targets){
        var scopes=jdbc.queryForList("SELECT company_id,department_id FROM management_scope WHERE id=?",request.get("scope_id"));if(scopes.size()!=1||targets.isEmpty())return false;
        var scope=scopes.getFirst();var systems=new HashSet<>(jdbc.queryForList("SELECT system_id FROM scope_system WHERE scope_id=?",String.class,request.get("scope_id")));
        for(var t:targets){if(!Objects.equals(scope.get("company_id"),t.get("company_id"))||!Objects.equals(scope.get("department_id"),t.get("department_id"))||!systems.contains(t.get("system_id")))return false;
            var roles=jdbc.queryForList("SELECT company_id,department_id,system_id FROM business_role WHERE id=?",t.get("role_id"));if(roles.size()!=1)return false;var role=roles.getFirst();
            if(!Objects.equals(role.get("company_id"),t.get("company_id"))||!Objects.equals(role.get("system_id"),t.get("system_id"))||role.get("department_id")!=null&&!Objects.equals(scope.get("department_id"),role.get("department_id"))||"ROLE_CONFIGURATION".equals(request.get("kind"))&&role.get("department_id")==null)return false;
        }
        return true;
    }
}
