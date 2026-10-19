package com.adgn.backend.query.management;

import com.adgn.backend.core.audit.AuditRecord;
import com.adgn.backend.core.transaction.CommandBoundary;
import com.adgn.backend.identity.repository.IdentityRepository;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.identity.service.CurrentIdentityReader;
import com.adgn.backend.scope.service.ManagementContextResolver;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.Set;

/** B-owned current access projection; every invocation belongs to the caller's snapshot/guard. */
public final class ManagementAccess {
    public record Context(String appointmentId,String appointmentVersion,String scopeVersion) { }
    public record Authority(IdentityRepository.Account account,String scopeId,String companyId,String departmentId,Set<String> systems) {
        public Authority {systems=Set.copyOf(systems);}
        public boolean global(){return scopeId==null;}
        public boolean system(String id){return global() || systems.contains(id);}
        public boolean role(String company,String department,String system,boolean write){
            return global() || companyId.equals(company) && system(system)
                    && (departmentId.equals(department) || !write && department==null);
        }
        public CommandBoundary.Identity<Authority> identity(){return new CommandBoundary.Identity<>(this,new AuditRecord.Actor(AuditRecord.ActorKind.ACCOUNT,account.id()));}
    }
    private final CurrentIdentityReader current;
    private final ManagementContextResolver contexts;
    public ManagementAccess(CurrentIdentityReader current,ManagementContextResolver contexts){this.current=current;this.contexts=contexts;}
    public Authority global(Jwt token){
        var account=current.admin(token,false).account();
        if(!account.superAdmin())throw new IdentityFailure(403,"FORBIDDEN");
        return new Authority(account,null,null,null,Set.of());
    }
    public Authority read(Jwt token,Context context){
        var resolved=contexts.resolve(token,new ManagementContextResolver.Selection(context.appointmentId(),context.appointmentVersion(),context.scopeVersion()));
        return new Authority(current.admin(token,false).account(),resolved.scopeId(),resolved.companyId(),resolved.departmentId(),resolved.systemIds());
    }
}
