package com.adgn.backend.catalog.service;

import com.adgn.backend.catalog.repository.SystemRepository;
import com.adgn.backend.catalog.repository.CatalogRepository;
import com.adgn.backend.core.transaction.CommandBoundary;
import com.adgn.backend.core.transaction.ReadBoundary;
import com.adgn.backend.core.transaction.WriteContext;
import com.adgn.backend.core.audit.*;
import com.adgn.backend.core.idempotency.*;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.query.management.*;
import com.adgn.backend.query.preview.PreviewService;
import com.adgn.backend.query.preview.ImpactPolicy;
import com.adgn.backend.query.preview.ImpactService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

public final class CatalogService {
    public record Edit(Long expectedVersion,String name,String description,Boolean enabled,String reason) { }
    public record Save(Edit input,String previewId) { }
    public record CreatedId(String localKey,String id) { }
    public record Result(String systemId,long catalogVersion,String outcome,List<CreatedId> createdIds,List<String> auditEventIds) { }
    private final CommandBoundary writes;
    private final ManagementAccess access;
    private final SystemRepository systems;
    private final PreviewService previews;
    private final ImpactService impacts;
    private final ReadBoundary reads;
    private final CatalogRepository catalog;
    private final CatalogPlanner planner;
    private final ObjectProvider<IdempotencyCoordinator> idempotency;
    private final AuditAppender audit;
    private final JsonMapper json;
    public CatalogService(CommandBoundary writes,ManagementAccess access,SystemRepository systems,PreviewService previews,ImpactService impacts,
            ReadBoundary reads,CatalogRepository catalog,ObjectProvider<IdempotencyCoordinator> idempotency,AuditAppender audit,JsonMapper json){
        this.writes=writes;this.access=access;this.systems=systems;this.previews=previews;this.impacts=impacts;this.reads=reads;this.catalog=catalog;
        this.planner=new CatalogPlanner(json);this.idempotency=idempotency;this.audit=audit;this.json=json;
    }
    public PreviewService.View systemPreview(Jwt token,String systemId,Edit input)throws Exception{
        String id=ManagementInput.identifier(systemId);Edit edit=normalize(input);
        return writes.execute(()->access.global(token).identity(),context->{
            var system=systems.get(id);if(system==null)throw new IdentityFailure(404,"NOT_FOUND");
            if(system.version()!=edit.expectedVersion())throw new IdentityFailure(409,"VERSION_CONFLICT");
            var analysis=impacts.analyze(context.authority(),ImpactPolicy.Change.system(id,edit.enabled()));
            return previews.create(context,context.authority(),new ManagementAccess.Context(null,null,null),"updateSystem:"+id,edit,
                    analysis.dependencies(),analysis.projection(),Map.of());
        });
    }
    public IdempotencyCoordinator.Outcome saveSystem(Jwt token,String systemId,String key,Save input,String requestId)throws Exception{
        String id=ManagementInput.identifier(systemId);if(input==null)throw IdentityFailure.invalid();Edit edit=normalize(input.input());String preview=ManagementInput.identifier(input.previewId());
        return execute(token,id,key,"updateSystem","PUT","/api/v1/admin/systems/"+id,List.of(edit,preview),context->{
            var basis=basis("updateSystem");var old=required(id);boolean changed=!old.name().equals(edit.name())||!old.description().equals(edit.description())||old.enabled()!=edit.enabled();
            try{
                if(old.version()!=edit.expectedVersion())throw new IdentityFailure(409,"VERSION_CONFLICT");
                var analysis=impacts.analyze(context.authority(),ImpactPolicy.Change.system(id,edit.enabled()));
                previews.confirm(context,context.authority(),globalContext(),preview,"updateSystem:"+id,edit,analysis.dependencies());
                if(changed){if(old.version()>=ManagementInput.MAX_VERSION)throw new IdentityFailure(409,"STATE_CONFLICT");catalog.requireWritable(id);}
            }catch(IdentityFailure failure){throw safe(failure,basis);}
            if(changed){catalog.system(context,old,edit.name(),edit.description(),edit.enabled());appendAudit(context,"SYSTEM_UPDATED",id,requestId,edit.reason(),Map.of("before",old,"after",required(id)));}
            return completion(required(id),basis);
        });
    }
    public PreviewService.View catalogPreview(Jwt token,String systemId,JsonNode input)throws Exception{
        String id=ManagementInput.identifier(systemId);var batch=CatalogInput.parse(input,false);
        return writes.execute(()->access.global(token).identity(),context->{
            var system=required(id);var bindings=new TreeMap<String,String>();for(var c:batch.commands())if(c.has("localKey"))bindings.put(c.get("localKey").stringValue(),context.newId().toString());
            var plan=planner.plan(id,system.catalogVersion(),catalog.snapshot(id),batch,bindings);var analysis=impacts.analyze(context.authority(),plan.impact());
            return previews.create(context,context.authority(),globalContext(),"saveCatalog:"+id,batch,analysis.dependencies(),analysis.projection(),bindings);
        });
    }
    public IdempotencyCoordinator.Outcome saveCatalog(Jwt token,String systemId,String key,JsonNode input,String requestId)throws Exception{
        String id=ManagementInput.identifier(systemId);var batch=CatalogInput.parse(input,true);String preview=CatalogInput.identifier(input.get("previewId"),false);
        return execute(token,id,key,"saveCatalog","POST","/api/v1/admin/systems/"+id+"/catalog-changes",List.of(batch,preview),context->{
            var basis=basis("saveCatalog");var system=required(id);var before=catalog.snapshot(id);CatalogPlanner.Plan plan;
            try{
                var bindings=previews.bindings(context,context.authority(),globalContext(),preview,"saveCatalog:"+id,batch);
                plan=planner.plan(id,system.catalogVersion(),before,batch,bindings);
                var analysis=impacts.analyze(context.authority(),plan.impact());previews.confirm(context,context.authority(),globalContext(),preview,"saveCatalog:"+id,batch,analysis.dependencies());
                if(plan.changed())catalog.requireWritable(id);
            }catch(IdentityFailure failure){throw safe(failure,basis);}
            var audits=new ArrayList<String>();
            if(plan.changed()){
                catalog.save(context,id,system.catalogVersion(),before,plan.after());
                audits.add(appendAudit(context,"CATALOG_CHANGED",id,requestId,batch.reason(),Map.of("before",before,"after",plan.after())));
            }
            var ids=plan.bindings().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->new CreatedId(e.getKey(),e.getValue())).toList();
            return completion(new Result(id,required(id).catalogVersion(),plan.changed()?"APPLIED":"NO_CHANGE",ids,audits),basis);
        });
    }
    public CatalogRepository.Versioned<CatalogRepository.Node> nodes(Jwt token,ManagementAccess.Context selection,String systemId,String page,String size,String parent,String type,String enabled,String root)throws Exception{
        String id=ManagementInput.identifier(systemId),parentId=parent==null?null:ManagementInput.identifier(parent);
        if(type!=null&&!Set.of("NAVIGATION","FUNCTION").contains(type))throw IdentityFailure.invalid();Boolean state=ManagementInput.bool(enabled),roots=ManagementInput.bool(root);if(Boolean.TRUE.equals(roots)&&parentId!=null)throw IdentityFailure.invalid();
        var pagination=ManagementInput.pagination(page,size);
        return reads.read(()->access.read(token,selection),a->{if(!a.system(id))throw new IdentityFailure(404,"NOT_FOUND");var system=required(id);return new CatalogRepository.Versioned<>(catalog.nodes(id,pagination,parentId,type,state,Boolean.TRUE.equals(roots)),system.catalogVersion());});
    }
    public CatalogRepository.Versioned<CatalogRepository.Permission> permissions(Jwt token,ManagementAccess.Context selection,String systemId,String page,String size,String function,String type,String enabled,String q)throws Exception{
        String id=ManagementInput.identifier(systemId),functionId=function==null?null:ManagementInput.identifier(function);if(type!=null&&!Set.of("SYSTEM_ACCESS","FUNCTION_OPERATION").contains(type))throw IdentityFailure.invalid();Boolean state=ManagementInput.bool(enabled);String query=q==null?null:ManagementInput.text(q,1,100);var pagination=ManagementInput.pagination(page,size);
        return reads.read(()->access.read(token,selection),a->{if(!a.system(id))throw new IdentityFailure(404,"NOT_FOUND");var system=required(id);return new CatalogRepository.Versioned<>(catalog.permissions(id,pagination,functionId,type,state,query),system.catalogVersion());});
    }
    private IdempotencyCoordinator.Outcome execute(Jwt token,String id,String key,String operation,String method,String path,Object input,CommandBoundary.CheckedFunction<WriteContext<ManagementAccess.Authority>,IdempotencyCoordinator.Completion> work)throws Exception{
        UUID parsed=ManagementInput.key(key);reads.read(()->access.global(token),a->{required(id);return null;});var coordinator=idempotency.getIfAvailable();if(coordinator==null)throw new IdentityFailure(503,"DEPENDENCY_UNAVAILABLE");
        var request=new IdempotencyRequest(UUID.fromString(token.getSubject()),operation,method,path,parsed,json.writeValueAsBytes(input));
        return coordinator.execute(request,()->access.global(token).identity(),(a,b)->{if(!a.global()||b.policy()!=AuthorizationBasis.Policy.GLOBAL_ONLY||!b.operationId().equals(operation))throw new IdentityFailure(403,"FORBIDDEN");},work::apply);
    }
    private SystemRepository.View required(String id){var system=systems.get(id);if(system==null)throw new IdentityFailure(404,"NOT_FOUND");return system;}
    private String appendAudit(WriteContext<?> context,String event,String id,String request,String reason,Object facts){return audit.append(context,new AuditRecord(event,id,null,null,new AuditRecord.Scope(AuditRecord.Visibility.GLOBAL,null,null,null),"SUCCEEDED",request,reason,json.writeValueAsString(Map.of("schemaVersion",1,"change",facts)),List.of(new AuditRecord.Subject("SYSTEM",id)))).toString();}
    private IdempotencyCoordinator.Completion completion(Object value,AuthorizationBasis basis){return new IdempotencyCoordinator.Completion(new SafeResult(200,json.writeValueAsString(value),null),basis);}
    private static AuthorizationBasis basis(String operation){return new AuthorizationBasis(operation,AuthorizationBasis.Policy.GLOBAL_ONLY,List.of());}
    private static RuntimeException safe(IdentityFailure failure,AuthorizationBasis basis){if(failure.status()==409||failure.status()==422)return new IdempotencyCoordinator.SafeFailure(failure.code(),basis);return failure;}
    private static ManagementAccess.Context globalContext(){return new ManagementAccess.Context(null,null,null);}
    private static Edit normalize(Edit input){
        if(input==null || input.enabled()==null)throw IdentityFailure.invalid();
        return new Edit(ManagementInput.version(input.expectedVersion()),ManagementInput.text(input.name(),1,100),ManagementInput.text(input.description(),0,500),input.enabled(),ManagementInput.text(input.reason(),1,500));
    }
}
