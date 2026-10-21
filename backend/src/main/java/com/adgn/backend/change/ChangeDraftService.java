package com.adgn.backend.change;

import com.adgn.backend.core.audit.*;
import com.adgn.backend.core.idempotency.*;
import com.adgn.backend.core.transaction.*;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.query.management.*;
import com.adgn.backend.role.repository.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.util.*;

public final class ChangeDraftService {
    private final ManagementAccess access;private final DraftRepository drafts;private final DraftPlanner planner;private final ClaimService claims;private final RoleDraftWriter roleWriter;private final RoleRepository roles;
    private final ObjectProvider<IdempotencyCoordinator> idempotency;private final AuditAppender audit;private final JsonMapper json;private final JdbcTemplate jdbc;
    public ChangeDraftService(DataSource source,ManagementAccess access,DraftRepository drafts,DraftPlanner planner,ClaimService claims,RoleDraftWriter roleWriter,RoleRepository roles,ObjectProvider<IdempotencyCoordinator> idempotency,AuditAppender audit,JsonMapper json){this.access=access;this.drafts=drafts;this.planner=planner;this.claims=claims;this.roleWriter=roleWriter;this.roles=roles;this.idempotency=idempotency;this.audit=audit;this.json=json;this.jdbc=new JdbcTemplate(source);}
    public IdempotencyCoordinator.Outcome createRole(Jwt token,ManagementAccess.Context selection,String key,JsonNode raw,String correlation)throws Exception{
        var input=DraftInput.role(raw);
        return execute(token,selection,key,"createRole","POST","/api/v1/admin/roles",input,c->{
            var a=c.authority();if(!a.role(input.companyId(),input.departmentId(),input.systemId(),true))throw new IdentityFailure(403,"FORBIDDEN");
            if(!exists("SELECT 1 FROM company WHERE id=?",input.companyId())||input.departmentId()!=null&&!exists("SELECT 1 FROM department WHERE id=? AND company_id=?",input.departmentId(),input.companyId())||!exists("SELECT 1 FROM business_system WHERE id=? AND enabled=1",input.systemId()))throw IdentityFailure.invalid();
            String role=roleWriter.create(c,input.name(),input.description(),input.companyId(),input.departmentId(),input.systemId());
            var content=json.valueToTree(Map.of("kind","ROLE_CONFIGURATION","companyId",input.companyId(),"roleId",role,"expectedConfigurationVersion",0,"permissionIds",List.of()));
            var targets=planner.plan(c,content);var draft=drafts.create(c,content,input.reason(),targets);claims.replace(c,draft.id(),draft.kind(),targets);
            record(c,"ROLE_DRAFT_CREATED",draft,targets,false,correlation);
            return complete(201,Map.of("role",roles.get(role),"draftId",draft.id(),"draftVersion",draft.draftVersion()),"/api/v1/admin/roles/"+role,basis("createRole",targets));
        });
    }
    public IdempotencyCoordinator.Outcome create(Jwt token,ManagementAccess.Context selection,String key,JsonNode raw,String correlation)throws Exception{
        var input=DraftInput.draft(raw,false);
        return execute(token,selection,key,"createChangeDraft","POST","/api/v1/admin/change-requests",input,c->{
            if(input.copiedFromRequestId()!=null){var original=visible(c.authority(),input.copiedFromRequestId());if(!original.state().equals("REJECTED"))throw new IdentityFailure(409,"STATE_CONFLICT");}
            var targets=planner.plan(c,input.content());var row=drafts.create(c,input.content(),input.reason(),targets);claims.replace(c,row.id(),row.kind(),targets);
            record(c,"CHANGE_DRAFT_CREATED",row,targets,false,correlation);
            return complete(201,drafts.view(row),"/api/v1/admin/change-requests/"+row.id(),basis("createChangeDraft",targets));
        });
    }
    public IdempotencyCoordinator.Outcome update(Jwt token,ManagementAccess.Context selection,String key,String id,JsonNode raw,String correlation)throws Exception{
        String request=ManagementInput.identifier(id);var input=DraftInput.draft(raw,true);
        return execute(token,selection,key,"updateChangeDraft","PUT","/api/v1/admin/change-requests/"+request,input,c->{
            var old=visible(c.authority(),request);var previous=drafts.targets(request);draft(old);
            if(!old.kind().equals(input.content().path("kind").stringValue())||!old.companyId().equals(input.content().path("companyId").stringValue())||old.kind().equals("ROLE_CONFIGURATION")&&!old.content().path("roleId").equals(input.content().path("roleId")))throw IdentityFailure.invalid();
            var targets=planner.plan(c,input.content());if(old.draftVersion()!=input.expectedDraftVersion())throw new IdentityFailure(409,"VERSION_CONFLICT");
            var both=new ArrayList<>(previous);both.addAll(targets);var basis=basis("updateChangeDraft",both);
            if(DraftInput.content(old.content()).equals(input.content())&&old.reason().equals(input.reason()))return complete(200,drafts.view(old),null,basis);
            if(old.draftVersion()>=ManagementInput.MAX_VERSION||old.rowVersion()>=ManagementInput.MAX_VERSION)throw new IdentityFailure(409,"STATE_CONFLICT");
            claims.replace(c,request,old.kind(),targets);var saved=drafts.update(c,old,input.content(),input.reason(),targets);record(c,"CHANGE_DRAFT_UPDATED",saved,both,false,correlation);
            return complete(200,drafts.view(saved),null,basis);
        });
    }
    public IdempotencyCoordinator.Outcome delete(Jwt token,ManagementAccess.Context selection,String key,String id,String expected,String correlation)throws Exception{
        String request=ManagementInput.identifier(id);long version;
        try{if(expected==null||!expected.matches("[0-9]+"))throw IdentityFailure.invalid();version=ManagementInput.version(Long.parseLong(expected));}catch(NumberFormatException bad){throw IdentityFailure.invalid();}
        return execute(token,selection,key,"deleteChangeDraft","DELETE","/api/v1/admin/change-requests/"+request,version,c->{
            var old=visible(c.authority(),request);draft(old);var targets=drafts.targets(request);var basis=basis("deleteChangeDraft",targets);
            if(old.draftVersion()!=version)throw new IdentityFailure(409,"VERSION_CONFLICT");
            record(c,"CHANGE_DRAFT_DELETED",old,targets,true,correlation);claims.release(c,request);drafts.delete(c,request);
            if(old.kind().equals("ROLE_CONFIGURATION"))roleWriter.deleteUnreferenced(c,old.content().path("roleId").stringValue());
            return complete(204,null,null,basis);
        });
    }
    private DraftRepository.Row visible(ManagementAccess.Authority a,String id){var r=drafts.get(id);if(r==null)throw new IdentityFailure(404,"NOT_FOUND");planner.authorize(a,drafts.targets(id),r.kind().equals("ROLE_CONFIGURATION"),true);return r;}
    private static void draft(DraftRepository.Row r){if(!r.state().equals("DRAFT"))throw new IdentityFailure(409,"STATE_CONFLICT");}
    private IdempotencyCoordinator.Outcome execute(Jwt token,ManagementAccess.Context selection,String key,String operation,String method,String path,Object input,CommandBoundary.CheckedFunction<WriteContext<ManagementAccess.Authority>,IdempotencyCoordinator.Completion> work)throws Exception{
        var parsed=ManagementInput.key(key);var coordinator=idempotency.getIfAvailable();if(coordinator==null)throw new IdentityFailure(503,"DEPENDENCY_UNAVAILABLE");
        // 选中任命是当前授权上下文，不是业务内容；重放允许另一当前完整覆盖的任命。
        var bytes=json.writeValueAsBytes(input);
        return coordinator.execute(new IdempotencyRequest(UUID.fromString(token.getSubject()),operation,method,path,parsed,bytes),()->access.read(token,selection).identity(),(a,b)->authorize(a,operation,b),work);
    }
    private AuthorizationBasis basis(String operation,List<DraftRepository.Target> targets){
        var tuples=new LinkedHashSet<AuthorizationBasis.Target>();
        for(var t:targets){
            if(t.department()==null)return new AuthorizationBasis(operation,AuthorizationBasis.Policy.GLOBAL_ONLY,List.of());
            tuples.add(new AuthorizationBasis.Target(UUID.fromString(t.company()),UUID.fromString(t.department()),UUID.fromString(t.system())));
            var owner=roles.ownership(t.role());if(owner!=null&&owner.departmentId()!=null)tuples.add(new AuthorizationBasis.Target(UUID.fromString(owner.companyId()),UUID.fromString(owner.departmentId()),UUID.fromString(owner.systemId())));
        }
        return new AuthorizationBasis(operation,AuthorizationBasis.Policy.SCOPED,List.copyOf(tuples));
    }
    private static void authorize(ManagementAccess.Authority a,String operation,AuthorizationBasis basis){
        boolean allowed=operation.equals(basis.operationId())&&(basis.policy()==AuthorizationBasis.Policy.GLOBAL_ONLY?a.global():basis.policy()==AuthorizationBasis.Policy.SCOPED&&!basis.targets().isEmpty()&&basis.targets().stream().allMatch(t->a.role(t.companyId().toString(),t.departmentId().toString(),t.systemId().toString(),true)));
        if(!allowed)throw new IdentityFailure(403,"FORBIDDEN");
    }
    private IdempotencyCoordinator.Completion complete(int status,Object body,String location,AuthorizationBasis basis){return new IdempotencyCoordinator.Completion(new SafeResult(status,body==null?null:json.writeValueAsString(body),location),basis);}
    private boolean exists(String sql,Object...args){return !jdbc.queryForList(sql,args).isEmpty();}
    private void record(WriteContext<?> c,String event,DraftRepository.Row row,List<DraftRepository.Target> targets,boolean deleting,String correlation){
        var tuples=new HashSet<List<String>>();for(var t:targets)tuples.add(Arrays.asList(t.company(),t.department(),t.system()));var first=targets.getFirst();boolean scoped=tuples.size()==1&&first.department()!=null;
        var scope=new AuditRecord.Scope(scoped?AuditRecord.Visibility.SCOPED:AuditRecord.Visibility.GLOBAL,UUID.fromString(row.companyId()),scoped?UUID.fromString(first.department()):null,scoped?UUID.fromString(first.system()):null);
        var subjects=new LinkedHashSet<AuditRecord.Subject>();subjects.add(new AuditRecord.Subject("CHANGE_REQUEST",row.id()));for(var t:targets){subjects.add(new AuditRecord.Subject("ROLE",t.role()));if(t.employee()!=null)subjects.add(new AuditRecord.Subject("EMPLOYEE",t.employee()));}
        audit.append(c,new AuditRecord(event,row.id(),deleting?null:UUID.fromString(row.id()),null,scope,"SUCCEEDED",correlation,row.reason(),json.writeValueAsString(Map.of("schemaVersion",1,"draft",drafts.view(row),"targets",targets)),List.copyOf(subjects)));
    }
}
