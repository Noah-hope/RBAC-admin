package com.adgn.backend.catalog.service;

import com.adgn.backend.catalog.repository.SystemRepository;
import com.adgn.backend.core.audit.*;
import com.adgn.backend.core.idempotency.*;
import com.adgn.backend.core.transaction.*;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.query.management.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

public final class SystemService {
    private final ReadBoundary reads;
    private final ManagementAccess access;
    private final SystemRepository repository;
    private final ObjectProvider<IdempotencyCoordinator> idempotency;
    private final AuditAppender audit;
    private final JsonMapper json;
    public SystemService(ReadBoundary reads,ManagementAccess access,SystemRepository repository,ObjectProvider<IdempotencyCoordinator> idempotency,AuditAppender audit,JsonMapper json){
        this.reads=reads;this.access=access;this.repository=repository;this.idempotency=idempotency;this.audit=audit;this.json=json;
    }
    public SystemRepository.View get(Jwt token,ManagementAccess.Context context,String id)throws Exception{
        String target=ManagementInput.identifier(id);
        return reads.read(()->access.read(token,context),a->{
            if(!a.system(target))throw new IdentityFailure(404,"NOT_FOUND");
            var result=repository.get(target);if(result==null)throw new IdentityFailure(404,"NOT_FOUND");return result;
        });
    }
    public SystemRepository.Page list(Jwt token,ManagementAccess.Context context,String p,String s,String enabled,String q)throws Exception{
        var page=ManagementInput.pagination(p,s);Boolean state=ManagementInput.bool(enabled);String query=q==null?null:ManagementInput.text(q,1,100);
        return reads.read(()->access.read(token,context),a->repository.list(a,page,state,query));
    }
    public IdempotencyCoordinator.Outcome create(Jwt token,String key,String code,String name,String description,String reason,String requestId)throws Exception{
        String c=ManagementInput.text(code,1,93),n=ManagementInput.text(name,1,100),d=description==null?"":ManagementInput.text(description,0,500),r=ManagementInput.text(reason,1,500);
        if(!c.matches("[a-zA-Z0-9][a-zA-Z0-9_.:-]*"))throw IdentityFailure.invalid();
        UUID parsed=ManagementInput.key(key);
        reads.read(()->access.global(token),a->null);
        var coordinator=idempotency.getIfAvailable();if(coordinator==null)throw new IdentityFailure(503,"DEPENDENCY_UNAVAILABLE");
        var basis=new AuthorizationBasis("createSystem",AuthorizationBasis.Policy.GLOBAL_ONLY,List.of());
        var request=new IdempotencyRequest(UUID.fromString(token.getSubject()),"createSystem","POST","/api/v1/admin/systems",parsed,json.writeValueAsBytes(List.of(c,n,d,r)));
        return coordinator.execute(request,()->access.global(token).identity(),(a,b)->{
            if(!a.global() || b.policy()!=AuthorizationBasis.Policy.GLOBAL_ONLY || !b.operationId().equals("createSystem"))throw new IdentityFailure(403,"FORBIDDEN");
        },context->{
            if(repository.duplicate(c))throw new IdempotencyCoordinator.SafeFailure("STATE_CONFLICT",basis);
            var value=repository.create(context,c,n,d);
            audit.append(context,new AuditRecord("SYSTEM_REGISTERED",value.id(),null,null,new AuditRecord.Scope(AuditRecord.Visibility.GLOBAL,null,null,null),"SUCCEEDED",requestId,r,
                    json.writeValueAsString(Map.of("schemaVersion",1,"system",value)),List.of(new AuditRecord.Subject("SYSTEM",value.id()))));
            return new IdempotencyCoordinator.Completion(new SafeResult(201,json.writeValueAsString(value),"/api/v1/admin/systems/"+value.id()),basis);
        });
    }
}
