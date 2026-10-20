package com.adgn.backend.scope.service;

import com.adgn.backend.core.audit.*;
import com.adgn.backend.core.idempotency.*;
import com.adgn.backend.core.transaction.*;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.query.management.*;
import com.adgn.backend.query.preview.*;
import com.adgn.backend.scope.repository.*;
import com.adgn.backend.web.scope.dto.ScopeResponses;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

public final class ScopeCreationService {
    public record Save(ScopeCreationInput input,String previewId){ }
    public record Result(ScopeResponses.Scope scope,List<String> auditEventIds,String outcome,int appointmentsChangedCount){ }
    private static final String OP="createScope";
    private static final ManagementAccess.Context GLOBAL=new ManagementAccess.Context(null,null,null);
    private final CommandBoundary writes;
    private final ManagementAccess access;
    private final PreviewService previews;
    private final ScopeCreationFacts facts;
    private final ScopeCreationRepository repository;
    private final ObjectProvider<IdempotencyCoordinator> coordinator;
    private final AuditAppender audit;
    private final JsonMapper json;
    public ScopeCreationService(CommandBoundary writes,ManagementAccess access,PreviewService previews,ScopeCreationFacts facts,ScopeCreationRepository repository,ObjectProvider<IdempotencyCoordinator> coordinator,AuditAppender audit,JsonMapper json){
        this.writes=writes;this.access=access;this.previews=previews;this.facts=facts;this.repository=repository;this.coordinator=coordinator;this.audit=audit;this.json=json;
    }
    public PreviewService.View preview(Jwt token,ScopeCreationInput raw)throws Exception{
        var input=ScopeCreationInput.normalized(raw);
        return writes.execute(()->access.global(token).identity(),context->{
            var dependencies=dependencies(input);var bindings=new TreeMap<String,String>();bindings.put("scope",context.newId().toString());
            for(String account:input.accountIds())bindings.put("appointment:"+account,context.newId().toString());
            return previews.create(context,context.authority(),GLOBAL,OP,input,dependencies,projection(input,bindings,context.authority()),bindings);
        });
    }
    public IdempotencyCoordinator.Outcome create(Jwt token,String key,Save raw,String requestId)throws Exception{
        if(raw==null)throw IdentityFailure.invalid();var input=ScopeCreationInput.normalized(raw.input());String preview=ManagementInput.identifier(raw.previewId());
        var parsedKey=ManagementInput.key(key);var service=coordinator.getIfAvailable();if(service==null)throw new IdentityFailure(503,"DEPENDENCY_UNAVAILABLE");
        var request=new IdempotencyRequest(UUID.fromString(token.getSubject()),OP,"POST","/api/v1/admin/management-scopes",parsedKey,json.writeValueAsBytes(List.of(input,preview)));
        return service.execute(request,()->access.global(token).identity(),(a,b)->{
            if(!a.global()||b.policy()!=AuthorizationBasis.Policy.GLOBAL_ONLY||!b.operationId().equals(OP))throw new IdentityFailure(403,"FORBIDDEN");
        },context->{
            var basis=new AuthorizationBasis(OP,AuthorizationBasis.Policy.GLOBAL_ONLY,List.of());
            ScopeResponses.Scope scope;Map<String,String> bindings;
            try{
                bindings=previews.bindings(context,context.authority(),GLOBAL,preview,OP,input);
                Map<String,Object> dependencies;
                try{dependencies=dependencies(input);}catch(IdentityFailure changed){throw PreviewService.stale();}
                previews.confirm(context,context.authority(),GLOBAL,preview,OP,input,dependencies);
                String id=Objects.requireNonNull(bindings.get("scope"),"Missing preview scope binding");
                var appointments=new TreeMap<String,String>();for(String account:input.accountIds())appointments.put(account,Objects.requireNonNull(bindings.get("appointment:"+account),"Missing preview appointment binding"));
                scope=repository.create(context,id,input.companyId(),input.departmentId(),input.systemIds(),appointments);
            }catch(IdentityFailure failure){if(failure.status()==409||failure.status()==422)throw new IdempotencyCoordinator.SafeFailure(failure.code(),basis);throw failure;}
            var subjects=new ArrayList<AuditRecord.Subject>();subjects.add(new AuditRecord.Subject("SCOPE",scope.id()));
            for(String account:input.accountIds())subjects.add(new AuditRecord.Subject("APPOINTMENT",bindings.get("appointment:"+account)));
            String event=audit.append(context,new AuditRecord("MANAGEMENT_SCOPE_CREATED",scope.id(),null,null,new AuditRecord.Scope(AuditRecord.Visibility.GLOBAL,null,null,null),"SUCCEEDED",requestId,input.reason(),
                    json.writeValueAsString(Map.of("schemaVersion",1,"scope",scope,"accountIds",input.accountIds(),"appointmentIds",input.accountIds().stream().map(a->bindings.get("appointment:"+a)).toList())),subjects)).toString();
            var result=new Result(scope,List.of(event),"APPLIED",input.accountIds().size());
            return new IdempotencyCoordinator.Completion(new SafeResult(201,json.writeValueAsString(result),"/api/v1/admin/management-scopes/"+scope.id()),basis);
        });
    }
    private PreviewService.Projection projection(ScopeCreationInput input,Map<String,String> bindings,ManagementAccess.Authority authority){
        var targets=new ArrayList<JsonNode>();
        for(String account:input.accountIds())targets.add(json.createObjectNode().put("targetType","APPOINTMENT").put("targetId",bindings.get("appointment:"+account))
                .put("companyId",input.companyId()).put("departmentId",input.departmentId()).putNull("systemId").putNull("source").putNull("targetVersion")
                .put("effect","CONFIGURATION_CHANGE").put("summary","新增后台管理任命；不授予业务权限"));
        var change=new ImpactPolicy.Change(Set.copyOf(input.systemIds()),Map.of(),Map.of(),Map.of(),Map.of(),Set.of(),Set.of(),Set.of(),Set.of(),List.of(),Set.of(),targets);
        return new ImpactPolicy(json).calculate(new ImpactPolicy.Facts(Map.of()),change,authority);
    }
    private Map<String,Object> dependencies(ScopeCreationInput input){
        // Hash canonical JSON facts, avoiding Java Long vs parsed JSON integer node equality.
        return Map.of("schemaVersion",1,"qualificationDigest",HexFormat.of().formatHex(previews.digest(facts.qualified(input))));
    }
}
