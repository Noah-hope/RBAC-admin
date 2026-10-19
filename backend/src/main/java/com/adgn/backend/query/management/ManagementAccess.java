package com.adgn.backend.query.management;

import com.adgn.backend.core.audit.AuditRecord;
import com.adgn.backend.core.transaction.CommandBoundary;
import com.adgn.backend.identity.repository.IdentityRepository;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.identity.service.CurrentIdentityReader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import javax.sql.DataSource;
import java.util.Set;

/** B-owned current access projection; every invocation belongs to the caller's snapshot/guard. */
public final class ManagementAccess {
    public record Context(String appointmentId,String appointmentVersion,String scopeVersion) { }
    public record Authority(IdentityRepository.Account account,String scopeId,String companyId,String departmentId,Set<String> systems) {
        public Authority {systems=Set.copyOf(systems);}
        public boolean global(){return account.superAdmin();}
        public boolean system(String id){return global() || systems.contains(id);}
        public boolean role(String company,String department,String system,boolean write){
            return global() || companyId.equals(company) && system(system)
                    && (departmentId.equals(department) || !write && department==null);
        }
        public CommandBoundary.Identity<Authority> identity(){return new CommandBoundary.Identity<>(this,new AuditRecord.Actor(AuditRecord.ActorKind.ACCOUNT,account.id()));}
    }
    private final CurrentIdentityReader current;
    private final JdbcTemplate jdbc;
    public ManagementAccess(CurrentIdentityReader current,DataSource source){this.current=current;this.jdbc=new JdbcTemplate(source);}
    public Authority global(Jwt token){
        var account=current.admin(token,false).account();
        if(!account.superAdmin())throw new IdentityFailure(403,"FORBIDDEN");
        return new Authority(account,null,null,null,Set.of());
    }
    public Authority read(Jwt token,Context context){
        var account=current.admin(token,false).account();
        if(account.superAdmin()){
            if(context.appointmentId()!=null || context.appointmentVersion()!=null || context.scopeVersion()!=null)throw IdentityFailure.invalid();
            return new Authority(account,null,null,null,Set.of());
        }
        String appointment=ManagementInput.identifier(context.appointmentId());
        if(context.appointmentVersion()==null || context.scopeVersion()==null)throw IdentityFailure.invalid();
        long av=headerVersion(context.appointmentVersion()),sv=headerVersion(context.scopeVersion());
        var rows=jdbc.query("""
            SELECT sc.id,sc.company_id,sc.department_id,sc.scope_version,ap.appointment_version
            FROM admin_appointment ap JOIN management_scope sc ON sc.id=ap.scope_id
            WHERE ap.id=? AND ap.account_id=? AND ap.status='ACTIVE' AND EXISTS (
              SELECT 1 FROM employment em WHERE em.employee_id=? AND em.company_id=sc.company_id
                AND em.department_id=sc.department_id AND em.status='ACTIVE')
            """,(rs,n)->new Scope(rs.getString("id"),rs.getString("company_id"),rs.getString("department_id"),rs.getLong("scope_version"),rs.getLong("appointment_version")),appointment,account.id(),account.employeeId());
        if(rows.size()!=1)throw changed();
        var scope=rows.getFirst();
        if(scope.version()!=sv || scope.appointmentVersion()!=av)throw changed();
        return new Authority(account,scope.id(),scope.company(),scope.department(),Set.copyOf(jdbc.queryForList("SELECT system_id FROM scope_system WHERE scope_id=?",String.class,scope.id())));
    }
    private record Scope(String id,String company,String department,long version,long appointmentVersion) { }
    private static long headerVersion(String value){
        try{if(!value.matches("[0-9]+"))throw new IllegalArgumentException();return ManagementInput.version(Long.valueOf(value));}
        catch(RuntimeException invalid){throw IdentityFailure.invalid();}
    }
    private static IdentityFailure changed(){return new IdentityFailure(409,"CONTEXT_CHANGED");}
}
