package com.adgn.backend.lifecycle;

import com.adgn.backend.core.audit.*;
import com.adgn.backend.core.idempotency.*;
import com.adgn.backend.core.transaction.*;
import com.adgn.backend.grant.*;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.query.management.*;
import com.adgn.backend.query.change.RequestVisibility;
import com.adgn.backend.query.preview.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.util.*;

/** One guarded command owns current authorization, fixed preview, all changes and the HTTP result. */
public final class SourceClosureService {
    public record SystemSelection(String systemId,long expectedSourceEpoch,long expectedVersion) { }
    public record Input(String employeeId,String companyId,String departmentId,List<SystemSelection> systems,String reason) { }
    private record Analysis(List<GrantReader.Source> sources,Map<String,Object> dependencies,PreviewService.Projection projection) { }
    private final JdbcTemplate jdbc; private final ManagementAccess access; private final CommandBoundary commands;
    private final ObjectProvider<IdempotencyCoordinator> idempotency; private final GrantReader grants;
    private final SourceCloser closer; private final PreviewService previews; private final ImpactService impacts;
    private final AuditAppender audit; private final JsonMapper json;
    private final RequestInvalidator invalidator;private final RequestVisibility requests;
    public SourceClosureService(DataSource source,ManagementAccess access,CommandBoundary commands,ObjectProvider<IdempotencyCoordinator> idempotency,
            GrantReader grants,SourceCloser closer,PreviewService previews,ImpactService impacts,AuditAppender audit,JsonMapper json,RequestInvalidator invalidator,RequestVisibility requests){
        this.jdbc=new JdbcTemplate(source);this.access=access;this.commands=commands;this.idempotency=idempotency;this.grants=grants;
        this.closer=closer;this.previews=previews;this.impacts=impacts;this.audit=audit;this.json=json;
        this.invalidator=invalidator;this.requests=requests;
    }
    public PreviewService.View preview(Jwt token,ManagementAccess.Context selection,JsonNode raw)throws Exception{
        var input=parse(raw);
        return commands.execute(()->qualify(token,selection,input).identity(),c->{
            var analysis=analyze(c.authority(),input);requireVersions(input,analysis.sources());
            return previews.create(c,c.authority(),selection,"closeSource",input,analysis.dependencies(),analysis.projection(),Map.of());
        });
    }
    public IdempotencyCoordinator.Outcome close(Jwt token,ManagementAccess.Context selection,String key,JsonNode raw,String requestId)throws Exception{
        object(raw,Set.of("input","previewId"));var input=parse(raw.get("input"));String preview=identifier(raw.get("previewId"));
        var coordinator=idempotency.getIfAvailable();if(coordinator==null)throw new IdentityFailure(503,"SERVICE_UNAVAILABLE");
        var request=new IdempotencyRequest(UUID.fromString(token.getSubject()),"closeSource","POST","/api/v1/admin/source-closures",ManagementInput.key(key),json.writeValueAsBytes(List.of(input,preview)));
        return coordinator.execute(request,()->qualify(token,selection,input).identity(),SourceClosureService::authorize,c->{
            var basis=basis(input);var before=analyze(c.authority(),input);
            try{previews.confirm(c,c.authority(),selection,preview,"closeSource",input,before.dependencies());}
            catch(IdentityFailure failure){if(failure.status()==409)throw new IdempotencyCoordinator.SafeFailure("PREVIEW_STALE",basis);throw failure;}
            requireVersions(input,before.sources());
            var selections=before.sources().stream().map(s->new SourceCloser.Selection(s.key(),s.sourceEpoch(),s.version())).toList();
            AffectedFacts facts;
            try{facts=closer.close(c,selections,input.reason());}
            catch(GrantMutationFailure failure){
                if("VERSION_EXHAUSTED".equals(failure.code()))throw new IdempotencyCoordinator.SafeFailure("STATE_CONFLICT",basis);
                if(Set.of("SOURCE_VERSION_CHANGED","RELATION_VERSION_CHANGED").contains(failure.code()))throw new IdempotencyCoordinator.SafeFailure("VERSION_CONFLICT",basis);
                throw failure;
            }
            var invalidated=invalidator.sources(c,facts,requestId,input.reason());
            var visible=invalidated.stream().filter(r->requests.visible(c.authority(),r.requestId())).map(RequestInvalidator.Invalidated::requestId).toList();
            var events=new ArrayList<String>();var updated=new ArrayList<Map<String,Object>>();
            for(var change:facts.sources()){
                var after=change.after();var k=after.key();
                updated.add(Map.of("source",Map.of("employeeId",k.employeeId(),"companyId",k.companyId(),"departmentId",k.departmentId(),"systemId",k.systemId()),"sourceEpoch",after.sourceEpoch(),"version",after.version()));
                var subjects=new ArrayList<AuditRecord.Subject>(List.of(new AuditRecord.Subject("SOURCE",after.id()),new AuditRecord.Subject("EMPLOYEE",k.employeeId())));
                visible.forEach(id->subjects.add(new AuditRecord.Subject("REQUEST",id)));
                events.add(audit.append(c,new AuditRecord("AUTHORIZATION_SOURCE_CLOSED",after.id(),null,null,
                        new AuditRecord.Scope(AuditRecord.Visibility.SCOPED,UUID.fromString(k.companyId()),UUID.fromString(k.departmentId()),UUID.fromString(k.systemId())),
                        "SUCCEEDED",requestId,input.reason(),json.writeValueAsString(Map.of("schemaVersion",1,"before",change.before(),"after",after,"invalidatedRequestIds",visible)),subjects)).toString());
            }
            long ended=facts.relations().stream().filter(r->r.grantId()!=null).map(AffectedFacts.RelationChange::grantId).distinct().count();
            boolean hidden=Boolean.TRUE.equals(before.projection().impact().get("hasUndisclosedImpact"))||visible.size()!=invalidated.size();
            var result=Map.of("outcome","APPLIED","updatedSources",updated,"auditEventIds",events,"invalidatedGrantCount",ended,"invalidatedRequestCount",visible.size(),
                    "visibility",hidden?"SCOPED":"COMPLETE","hasUndisclosedImpact",hidden);
            var savedBasis=resultBasis(input,visible);
            return new IdempotencyCoordinator.Completion(new SafeResult(200,json.writeValueAsString(result),null),savedBasis);
        });
    }
    private ManagementAccess.Authority qualify(Jwt token,ManagementAccess.Context context,Input input){
        var authority=access.read(token,context);
        for(var system:input.systems()){
            if(!authority.role(input.companyId(),input.departmentId(),system.systemId(),true))throw new IdentityFailure(403,"FORBIDDEN");
            grants.source(new GrantReader.SourceKey(input.employeeId(),input.companyId(),input.departmentId(),system.systemId()));
        }
        return authority;
    }
    private Analysis analyze(ManagementAccess.Authority authority,Input input){
        var selected=new ArrayList<GrantReader.Source>();var sources=new HashSet<ImpactPolicy.Source>();var systems=new HashSet<String>();
        for(var system:input.systems()){
            selected.add(grants.source(new GrantReader.SourceKey(input.employeeId(),input.companyId(),input.departmentId(),system.systemId())));
            systems.add(system.systemId());sources.add(new ImpactPolicy.Source(input.employeeId(),input.companyId(),input.departmentId(),system.systemId()));
        }
        var change=new ImpactPolicy.Change(systems,Map.of(),Map.of(),Map.of(),Map.of(),sources,Set.of(),Set.of(),Set.of(),List.of(),Set.of(),List.of());
        var analysis=impacts.analyze(authority,change);
        var targets=new ArrayList<JsonNode>();boolean hidden=Boolean.TRUE.equals(analysis.projection().impact().get("hasUndisclosedImpact"));
        for(var target:analysis.projection().targets()){
            if("CHANGE_REQUEST".equals(target.path("targetType").stringValue())&&!requests.visible(authority,target.path("targetId").stringValue()))hidden=true;
            else targets.add(target);
        }
        for(var s:selected){var k=s.key();boolean found=targets.stream().anyMatch(t->"SOURCE".equals(t.path("targetType").stringValue())&&k.employeeId().equals(t.path("source").path("employeeId").stringValue())&&k.companyId().equals(t.path("source").path("companyId").stringValue())&&k.departmentId().equals(t.path("source").path("departmentId").stringValue())&&k.systemId().equals(t.path("source").path("systemId").stringValue()));
            if(!found){var target=json.createObjectNode().put("targetType","SOURCE").putNull("targetId").put("companyId",k.companyId()).put("departmentId",k.departmentId()).put("systemId",k.systemId()).put("targetVersion",s.version()).put("effect","CLOSE_SOURCE").put("summary","关闭准确来源并推进授权周期");
                target.set("source",json.valueToTree(new ImpactPolicy.Source(k.employeeId(),k.companyId(),k.departmentId(),k.systemId())));targets.add(target);}
        }
        var counts=new LinkedHashMap<>(analysis.projection().impact());counts.put("visibleSourceCount",targets.stream().filter(t->"SOURCE".equals(t.path("targetType").stringValue())).count());counts.put("visiblePendingRequestCount",targets.stream().filter(t->"CHANGE_REQUEST".equals(t.path("targetType").stringValue())).count());
        counts.put("hasUndisclosedImpact",hidden);counts.put("warning",hidden?"存在不可披露的影响，仅显示当前范围内明细":"");
        var projection=new PreviewService.Projection(counts,analysis.projection().employees(),targets,hidden?List.of("存在不可披露的影响"):List.of());
        return new Analysis(List.copyOf(selected),Map.of("sources",HexFormat.of().formatHex(previews.digest(selected)),"impact",analysis.dependencies()),projection);
    }
    private static void requireVersions(Input input,List<GrantReader.Source> sources){
        for(int n=0;n<sources.size();n++){var expected=input.systems().get(n);var actual=sources.get(n);
            if(expected.expectedSourceEpoch()!=actual.sourceEpoch()||expected.expectedVersion()!=actual.version())throw new IdentityFailure(409,"SOURCE_VERSION_CHANGED");}
    }
    private static AuthorizationBasis basis(Input input){return new AuthorizationBasis("closeSource",AuthorizationBasis.Policy.SCOPED,input.systems().stream()
            .map(s->new AuthorizationBasis.Target(UUID.fromString(input.companyId()),UUID.fromString(input.departmentId()),UUID.fromString(s.systemId()))).toList());}
    private AuthorizationBasis resultBasis(Input input,List<String> visibleRequests){
        var targets=new HashSet<>(basis(input).targets());
        for(String id:visibleRequests)for(var row:jdbc.queryForList("SELECT t.company_id,t.department_id,t.system_id,r.department_id AS role_department FROM change_target t JOIN business_role r ON r.id=t.role_id WHERE t.request_id=? ORDER BY t.line_no",id)){
            if(row.get("department_id")==null)return new AuthorizationBasis("closeSource",AuthorizationBasis.Policy.GLOBAL_ONLY,List.of());
            UUID company=UUID.fromString((String)row.get("company_id")),system=UUID.fromString((String)row.get("system_id"));
            targets.add(new AuthorizationBasis.Target(company,UUID.fromString((String)row.get("department_id")),system));
            if(row.get("role_department")!=null)targets.add(new AuthorizationBasis.Target(company,UUID.fromString((String)row.get("role_department")),system));
        }
        return new AuthorizationBasis("closeSource",AuthorizationBasis.Policy.SCOPED,targets.stream().sorted(Comparator.comparing(t->t.companyId()+":"+t.departmentId()+":"+t.systemId())).toList());
    }
    private static void authorize(ManagementAccess.Authority a,AuthorizationBasis basis){
        if("closeSource".equals(basis.operationId())&&basis.policy()==AuthorizationBasis.Policy.GLOBAL_ONLY&&a.global())return;
        if(!"closeSource".equals(basis.operationId())||basis.policy()!=AuthorizationBasis.Policy.SCOPED||basis.targets()==null||basis.targets().isEmpty()
                ||!basis.targets().stream().allMatch(t->a.role(t.companyId().toString(),t.departmentId().toString(),t.systemId().toString(),true)))throw new IdentityFailure(403,"FORBIDDEN");
    }
    private static Input parse(JsonNode raw){
        object(raw,Set.of("employeeId","companyId","departmentId","systems","reason"));
        String e=identifier(raw.get("employeeId")),c=identifier(raw.get("companyId")),d=identifier(raw.get("departmentId"));
        var systems=raw.get("systems");if(systems==null||!systems.isArray()||systems.size()<1||systems.size()>100)throw IdentityFailure.invalid();
        var selections=new TreeMap<String,SystemSelection>();
        for(var system:systems){object(system,Set.of("systemId","expectedSourceEpoch","expectedVersion"));String id=identifier(system.get("systemId"));
            if(selections.put(id,new SystemSelection(id,version(system.get("expectedSourceEpoch")),version(system.get("expectedVersion"))))!=null)throw IdentityFailure.invalid();}
        var reason=raw.get("reason");if(reason==null||!reason.isString())throw IdentityFailure.invalid();
        return new Input(e,c,d,List.copyOf(selections.values()),ManagementInput.text(reason.stringValue(),1,500));
    }
    private static String identifier(JsonNode value){if(value==null||!value.isString())throw IdentityFailure.invalid();return ManagementInput.identifier(value.stringValue());}
    private static long version(JsonNode value){if(value==null||!value.isIntegralNumber()||!value.canConvertToLong())throw IdentityFailure.invalid();return ManagementInput.version(value.longValue());}
    private static void object(JsonNode value,Set<String> fields){if(value==null||!value.isObject()||!value.propertyNames().equals(fields))throw IdentityFailure.invalid();}
}
