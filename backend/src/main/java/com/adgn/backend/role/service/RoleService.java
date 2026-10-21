package com.adgn.backend.role.service;

import com.adgn.backend.role.repository.RoleRepository;
import com.adgn.backend.core.audit.*;
import com.adgn.backend.core.idempotency.*;
import com.adgn.backend.core.transaction.ReadBoundary;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.query.management.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

public final class RoleService {
    private final ReadBoundary reads;
    private final ManagementAccess access;
    private final RoleRepository repository;
    private final ObjectProvider<IdempotencyCoordinator> idempotency;
    private final AuditAppender audit;
    private final JsonMapper json;
    public RoleService(ReadBoundary reads,ManagementAccess access,RoleRepository repository,ObjectProvider<IdempotencyCoordinator> idempotency,AuditAppender audit,JsonMapper json){this.reads=reads;this.access=access;this.repository=repository;this.idempotency=idempotency;this.audit=audit;this.json=json;}
    public RoleRepository.View get(Jwt token,ManagementAccess.Context context,String id)throws Exception{
        String role=ManagementInput.identifier(id);
        return reads.read(()->access.read(token,context),a->target(a,role,false));
    }
    public RoleRepository.Page list(Jwt token,ManagementAccess.Context context,String page,String size,String company,String department,String system,String state,String q)throws Exception{
        var pagination=ManagementInput.pagination(page,size);
        if(company!=null)company=ManagementInput.identifier(company);if(department!=null)department=ManagementInput.identifier(department);if(system!=null)system=ManagementInput.identifier(system);
        if(state!=null && !Set.of("UNCONFIGURED","EFFECTIVE").contains(state))throw IdentityFailure.invalid();
        var filter=new RoleRepository.Filter(company,department,system,state,q==null?null:ManagementInput.text(q,1,100));
        return reads.read(()->access.read(token,context),a->{
            if(!a.global() && (filter.company()!=null && !a.companyId().equals(filter.company()) || filter.department()!=null && !a.departmentId().equals(filter.department()) || filter.system()!=null && !a.system(filter.system())))throw new IdentityFailure(403,"FORBIDDEN");
            return repository.list(a,pagination,filter);
        });
    }
    public IdempotencyCoordinator.Outcome metadata(Jwt token,ManagementAccess.Context context,String id,String key,Long expected,String name,String description,String reason,String requestId)throws Exception{
        String role=ManagementInput.identifier(id),n=ManagementInput.text(name,1,100),d=ManagementInput.text(description,0,500),r=ManagementInput.text(reason,1,500);
        long version=ManagementInput.version(expected);UUID parsed=ManagementInput.key(key);
        reads.read(()->qualify(token,context,role),a->null);
        var coordinator=idempotency.getIfAvailable();if(coordinator==null)throw new IdentityFailure(503,"DEPENDENCY_UNAVAILABLE");
        // Typed normalized request bytes include the selected management context, not RFC8785 fixed OA bytes.
        var fields=Arrays.asList(role,version,n,d,r,context.appointmentId(),context.appointmentVersion(),context.scopeVersion());
        var request=new IdempotencyRequest(UUID.fromString(token.getSubject()),"updateRoleMetadata","PUT","/api/v1/admin/roles/"+role+"/metadata",parsed,json.writeValueAsBytes(fields));
        return coordinator.execute(request,()->qualify(token,context,role).identity(),RoleService::authorize,write->{
            var old=target(write.authority(),role,true);var basis=basis(old);
            if(old.metadataVersion()!=version)throw new IdempotencyCoordinator.SafeFailure("VERSION_CONFLICT",basis);
            if(old.name().equals(n) && old.description().equals(d))return completion(old,basis);
            if(version>=ManagementInput.MAX_VERSION)throw new IdempotencyCoordinator.SafeFailure("STATE_CONFLICT",basis);
            var value=repository.metadata(write,old,n,d);
            var visibility=value.departmentId()==null?AuditRecord.Visibility.GLOBAL:AuditRecord.Visibility.SCOPED;
            audit.append(write,new AuditRecord("ROLE_METADATA_UPDATED",role,null,null,new AuditRecord.Scope(visibility,UUID.fromString(value.companyId()),value.departmentId()==null?null:UUID.fromString(value.departmentId()),UUID.fromString(value.systemId())),"SUCCEEDED",requestId,r,
                    json.writeValueAsString(Map.of("schemaVersion",1,"role",value)),List.of(new AuditRecord.Subject("ROLE",role))));
            return completion(value,basis);
        });
    }
    private ManagementAccess.Authority qualify(Jwt token,ManagementAccess.Context context,String role){var a=access.read(token,context);target(a,role,true);return a;}
    private RoleRepository.View target(ManagementAccess.Authority a,String id,boolean write){
        // Authorize fixed ownership before materializing potentially damaged/hidden configuration.
        var owner=repository.ownership(id);
        if(owner==null || !a.role(owner.companyId(),owner.departmentId(),owner.systemId(),false))throw new IdentityFailure(404,"NOT_FOUND");
        if(write && !a.role(owner.companyId(),owner.departmentId(),owner.systemId(),true))throw new IdentityFailure(403,"FORBIDDEN");
        return repository.get(id);
    }
    private IdempotencyCoordinator.Completion completion(RoleRepository.View value,AuthorizationBasis basis){return new IdempotencyCoordinator.Completion(new SafeResult(200,json.writeValueAsString(value),null),basis);}
    private static AuthorizationBasis basis(RoleRepository.View value){
        if(value.departmentId()==null)return new AuthorizationBasis("updateRoleMetadata",AuthorizationBasis.Policy.GLOBAL_ONLY,List.of());
        return new AuthorizationBasis("updateRoleMetadata",AuthorizationBasis.Policy.SCOPED,List.of(new AuthorizationBasis.Target(UUID.fromString(value.companyId()),UUID.fromString(value.departmentId()),UUID.fromString(value.systemId()))));
    }
    private static void authorize(ManagementAccess.Authority a,AuthorizationBasis basis){
        if(!basis.operationId().equals("updateRoleMetadata"))throw new IdentityFailure(403,"FORBIDDEN");
        if(basis.policy()==AuthorizationBasis.Policy.GLOBAL_ONLY){if(a.global())return;}
        else if(basis.policy()==AuthorizationBasis.Policy.SCOPED && basis.targets().stream().allMatch(t->a.role(t.companyId().toString(),t.departmentId().toString(),t.systemId().toString(),true)))return;
        throw new IdentityFailure(403,"FORBIDDEN");
    }
}
