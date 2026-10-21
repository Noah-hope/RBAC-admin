package com.adgn.backend.lifecycle;

import com.adgn.backend.core.idempotency.*;
import com.adgn.backend.core.transaction.*;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.query.management.*;
import com.adgn.backend.query.preview.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static com.adgn.backend.lifecycle.LifecycleStore.*;

public final class LifecycleService {
    private static final ManagementAccess.Context GLOBAL=new ManagementAccess.Context(null,null,null);
    private final LifecycleStore db;private final ManagementAccess access;private final CommandBoundary commands;
    private final ObjectProvider<IdempotencyCoordinator> idempotency;private final PreviewService previews;private final ImpactService impacts;
    private final EmployeeStateWriter employees;private final ScopeReplacementService scopes;private final JsonMapper json;
    LifecycleService(LifecycleStore db,ManagementAccess access,CommandBoundary commands,ObjectProvider<IdempotencyCoordinator> idempotency,PreviewService previews,ImpactService impacts,EmployeeStateWriter employees,ScopeReplacementService scopes,JsonMapper json){this.db=db;this.access=access;this.commands=commands;this.idempotency=idempotency;this.previews=previews;this.impacts=impacts;this.employees=employees;this.scopes=scopes;this.json=json;}
    public PreviewService.View employeePreview(Jwt token,String employee,LifecycleInput.EmployeeState raw)throws Exception{
        String id=ManagementInput.identifier(employee);if(raw==null)throw IdentityFailure.invalid();var in=raw.normalized();
        return commands.execute(()->access.global(token).identity(),c->{
            var old=db.employee(id);requireVersion(number(old,"version"),in.expectedVersion());
            if(in.status().equals("INACTIVE")&&!in.status().equals(old.get("status")))db.preventSelfLock(c,id);
            var analysis=employeeAnalysis(c.authority(),id,in);
            return previews.create(c,c.authority(),GLOBAL,"setEmployeeState",List.of(id,in),analysis.dependencies(),analysis.projection(),Map.of());
        });
    }
    public PreviewService.View scopePreview(Jwt token,String scope,LifecycleInput.ScopeEdit raw)throws Exception{
        String id=ManagementInput.identifier(scope);if(raw==null)throw IdentityFailure.invalid();var in=raw.normalized();
        return commands.execute(()->access.global(token).identity(),c->{
            requireVersion(number(db.scope(id),"scope_version"),in.expectedVersion());
            var dependencies=hashed(db.scopeDependencies(id,in));
            var bindings=new TreeMap<String,String>();
            var active=db.appointments(id).stream().map(ap->(String)ap.get("account_id")).toList();
            for(String account:in.accountIds())if(!active.contains(account))bindings.put("appointment:"+account,c.newId().toString());
            return previews.create(c,c.authority(),GLOBAL,"replaceScope",List.of(id,in),dependencies,scopeProjection(id,in,c.authority(),bindings),bindings);
        });
    }
    public IdempotencyCoordinator.Outcome employeeState(Jwt token,String employee,String key,LifecycleInput.EmployeeSave raw,String correlation)throws Exception{
        String id=ManagementInput.identifier(employee);if(raw==null||raw.input()==null)throw IdentityFailure.invalid();var in=raw.input().normalized();String preview=ManagementInput.identifier(raw.previewId());
        return execute(token,key,"setEmployeeState","/api/v1/admin/employees/"+id+"/state",List.of(id,in,preview),c->{
            var analysis=employeeAnalysis(c.authority(),id,in);previews.confirm(c,c.authority(),GLOBAL,preview,"setEmployeeState",List.of(id,in),analysis.dependencies());
            return employees.apply(c,id,in,correlation);
        });
    }
    public IdempotencyCoordinator.Outcome replaceScope(Jwt token,String scope,String key,LifecycleInput.ScopeSave raw,String correlation)throws Exception{
        String id=ManagementInput.identifier(scope);if(raw==null||raw.input()==null)throw IdentityFailure.invalid();var in=raw.input().normalized();String preview=ManagementInput.identifier(raw.previewId());
        return execute(token,key,"replaceScope","/api/v1/admin/management-scopes/"+id,List.of(id,in,preview),c->{
            Map<String,Object> dependencies;
            try{dependencies=hashed(db.scopeDependencies(id,in));}catch(IdentityFailure stale){throw PreviewService.stale();}
            previews.confirm(c,c.authority(),GLOBAL,preview,"replaceScope",List.of(id,in),dependencies);
            var bindings=previews.bindings(c,c.authority(),GLOBAL,preview,"replaceScope",List.of(id,in));
            return scopes.apply(c,id,in,bindings,correlation);
        });
    }
    private IdempotencyCoordinator.Outcome execute(Jwt token,String key,String operation,String path,Object input,CommandBoundary.CheckedFunction<WriteContext<ManagementAccess.Authority>,Object> work)throws Exception{
        var service=idempotency.getIfAvailable();if(service==null)throw new IdentityFailure(503,"DEPENDENCY_UNAVAILABLE");
        var basis=new AuthorizationBasis(operation,AuthorizationBasis.Policy.GLOBAL_ONLY,List.of());
        var request=new IdempotencyRequest(UUID.fromString(token.getSubject()),operation,"PUT",path,ManagementInput.key(key),json.writeValueAsBytes(input));
        return service.execute(request,()->access.global(token).identity(),(a,b)->{if(!a.global()||b.policy()!=AuthorizationBasis.Policy.GLOBAL_ONLY||!operation.equals(b.operationId()))throw new IdentityFailure(403,"FORBIDDEN");},c->{
            try{return new IdempotencyCoordinator.Completion(new SafeResult(200,json.writeValueAsString(work.apply(c)),null),basis);}
            catch(IdentityFailure failure){if(failure.status()==409||failure.status()==422)throw new IdempotencyCoordinator.SafeFailure(failure.status()==422?"VALIDATION_ERROR":Set.of("VERSION_CONFLICT","STATE_CONFLICT","PREVIEW_STALE").contains(failure.code())?failure.code():"STATE_CONFLICT",basis);throw failure;}
        });
    }
    private record Analysis(Map<String,Object> dependencies,PreviewService.Projection projection) { }
    private Analysis employeeAnalysis(ManagementAccess.Authority a,String id,LifecycleInput.EmployeeState in){
        var facts=db.employeeDependencies(id);var old=db.employee(id);
        boolean disabling=in.status().equals("INACTIVE")&&!in.status().equals(old.get("status"));
        var targets=new ArrayList<JsonNode>();
        var sources=db.jdbc.queryForList("SELECT id,employee_id,company_id,department_id,system_id,source_version FROM authorization_source WHERE employee_id=? ORDER BY id",id);
        var systems=new TreeSet<String>();sources.forEach(s->systems.add((String)s.get("system_id")));
        PreviewService.Projection result=empty(a,List.of());Map<String,Object> impactDependencies=Map.of();
        if(disabling){
            for(var ap:db.jdbc.queryForList("SELECT ap.id,ap.appointment_version,sc.company_id,sc.department_id FROM admin_appointment ap JOIN admin_account ac ON ac.id=ap.account_id JOIN management_scope sc ON sc.id=ap.scope_id WHERE ac.employee_id=? AND ap.status='ACTIVE' ORDER BY ap.id",id))targets.add(target("APPOINTMENT",(String)ap.get("id"),ap.get("company_id"),ap.get("department_id"),null,number(ap,"appointment_version"),"REVOKE_APPOINTMENT"));
            for(var s:sources){var target=target("SOURCE",(String)s.get("id"),s.get("company_id"),s.get("department_id"),s.get("system_id"),number(s,"source_version"),"CLOSE_SOURCE");target.set("source",json.valueToTree(Map.of("employeeId",id,"companyId",s.get("company_id"),"departmentId",s.get("department_id"),"systemId",s.get("system_id"))));targets.add(target);}
            if(!systems.isEmpty()){
                var change=new ImpactPolicy.Change(systems,Map.of(),Map.of(),Map.of(),Map.of(),Set.of(),Set.of(),Set.of(id),Set.of(),List.of(),Set.of(),List.of());
                var impact=impacts.analyze(a,change);result=impact.projection();impactDependencies=impact.dependencies();
            }
        }
        var merged=new LinkedHashMap<String,JsonNode>();for(var t:result.targets())merged.put(t.path("targetType").asText()+":"+t.path("targetId").asText(),t);for(var t:targets)merged.putIfAbsent(t.path("targetType").asText()+":"+t.path("targetId").asText(),t);
        var counts=new LinkedHashMap<>(result.impact());counts.put("visibleAppointmentCount",targets.stream().filter(t->t.path("targetType").asText().equals("APPOINTMENT")).count());counts.put("visibleSourceCount",disabling?sources.size():0);
        List<String> warnings=in.status().equals("ACTIVE")?List.of("恢复仅恢复员工身份，不恢复旧授权、任命或会话"):List.of();
        return new Analysis(hashed(Map.of("facts",facts,"impact",impactDependencies)),new PreviewService.Projection(counts,result.employees(),new ArrayList<>(merged.values()),warnings));
    }
    private PreviewService.Projection scopeProjection(String id,LifecycleInput.ScopeEdit in,ManagementAccess.Authority a,Map<String,String> bindings){
        var old=db.scope(id);var aps=db.appointments(id);var targets=new ArrayList<JsonNode>();var retained=new HashSet<String>();
        for(var ap:aps){String account=(String)ap.get("account_id");if(in.accountIds().contains(account))retained.add(account);else targets.add(target("APPOINTMENT",(String)ap.get("id"),old.get("company_id"),old.get("department_id"),null,number(ap,"appointment_version"),"REVOKE_APPOINTMENT"));}
        for(String account:in.accountIds())if(!retained.contains(account))targets.add(target("APPOINTMENT",bindings.get("appointment:"+account),in.companyId(),in.departmentId(),null,null,"CONFIGURATION_CHANGE"));
        // Preview the same whole-request coverage boundary used by RequestInvalidator.
        for(var r:db.jdbc.queryForList("SELECT id,kind,status_version FROM change_request WHERE scope_id=? AND submitted_version IS NOT NULL AND state IN ('PENDING','APPROVED','EXECUTION_FAILED') ORDER BY id",id)){
            var lines=db.jdbc.queryForList("SELECT t.company_id,t.department_id,t.system_id,r.department_id AS role_department FROM change_target t JOIN business_role r ON r.id=t.role_id WHERE t.request_id=? ORDER BY t.line_no",r.get("id"));
            boolean covered=!lines.isEmpty()&&lines.stream().allMatch(t->in.companyId().equals(t.get("company_id"))&&in.departmentId().equals(t.get("department_id"))&&in.systemIds().contains(t.get("system_id"))&&(t.get("role_department")==null||in.departmentId().equals(t.get("role_department")))&&(!"ROLE_CONFIGURATION".equals(r.get("kind"))||t.get("role_department")!=null));
            if(!covered)targets.add(target("CHANGE_REQUEST",(String)r.get("id"),in.companyId(),null,null,number(r,"status_version"),"CONFIGURATION_CHANGE"));
        }
        return empty(a,targets);
    }
    private PreviewService.Projection empty(ManagementAccess.Authority a,List<JsonNode> targets){
        return new ImpactPolicy(json).calculate(new ImpactPolicy.Facts(Map.of()),new ImpactPolicy.Change(Set.of(),Map.of(),Map.of(),Map.of(),Map.of(),Set.of(),Set.of(),Set.of(),Set.of(),List.of(),Set.of(),targets),a);
    }
    private tools.jackson.databind.node.ObjectNode target(String type,String id,Object company,Object department,Object system,Long version,String effect){
        var t=json.createObjectNode().put("targetType",type).put("targetId",id).put("effect",effect).put("summary","状态或管理范围变更影响");t.set("companyId",json.valueToTree(company));t.set("departmentId",json.valueToTree(department));t.set("systemId",json.valueToTree(system));t.set("targetVersion",json.valueToTree(version));t.putNull("source");return t;
    }
    private Map<String,Object> hashed(Object facts){return Map.of("schemaVersion",1,"digest",HexFormat.of().formatHex(previews.digest(facts)));}
    private static void requireVersion(long actual,long expected){if(actual!=expected)throw new IdentityFailure(409,"VERSION_CONFLICT");}
}
