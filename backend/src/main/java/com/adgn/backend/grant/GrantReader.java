package com.adgn.backend.grant;

import com.adgn.backend.people.OrganizationReader;
import com.adgn.backend.core.transaction.PersistenceFailure;
import com.adgn.backend.identity.security.IdentityFailure;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.util.*;

/** Current primary-database facts. Callers own both their transaction and authorization. */
public final class GrantReader {
    public record SourceKey(String employeeId,String companyId,String departmentId,String systemId){ }
    public record Key(String employeeId,String roleId,String sourceCompanyId,String sourceDepartmentId){ }
    public record Source(String id,SourceKey key,long sourceEpoch,long version){ }
    public record Role(String id,String companyId,String departmentId,String systemId,String state,long configurationVersion,boolean systemEnabled){ }
    public record Baseline(String id,Key key,Role role,Source source,long relationVersion,String activeGrantId,
                           List<OrganizationReader.Employment> employments,boolean eligibleForGrant){
        public Baseline{employments=List.copyOf(employments);}
    }
    public record Grant(String id,String employeeId,String roleId,String companyId,String sourceDepartmentId,String systemId,
                        List<String> employmentIds,long sourceEpoch,long relationVersion,String status,String endedReason,Instant createdAt){
        public Grant{employmentIds=List.copyOf(employmentIds);}
    }
    private final DataSource dataSource;private final JdbcTemplate jdbc;private final OrganizationReader people;
    public GrantReader(DataSource source,OrganizationReader people){this.dataSource=Objects.requireNonNull(source);this.jdbc=new JdbcTemplate(source);this.people=Objects.requireNonNull(people);}
    public Role role(String id){
        transaction();var roles=jdbc.query("SELECT r.id,r.company_id,r.department_id,r.system_id,r.state,r.configuration_version,s.enabled FROM business_role r JOIN business_system s ON s.id=r.system_id WHERE r.id=?",
            (r,n)->new Role(r.getString("id"),r.getString("company_id"),r.getString("department_id"),r.getString("system_id"),r.getString("state"),r.getLong("configuration_version"),r.getBoolean("enabled")),id);
        if(roles.isEmpty())throw missing();return roles.getFirst();
    }
    public Source source(SourceKey key){
        transaction();
        if(!exists("SELECT 1 FROM employee WHERE id=?",key.employeeId()) || !exists("SELECT 1 FROM company WHERE id=?",key.companyId())
                || !exists("SELECT 1 FROM department WHERE id=?",key.departmentId()) || !exists("SELECT 1 FROM business_system WHERE id=?",key.systemId()))throw missing();
        if(!exists("SELECT 1 FROM department WHERE id=? AND company_id=?",key.departmentId(),key.companyId()))throw IdentityFailure.invalid();
        var rows=jdbc.query("SELECT id,source_epoch,source_version FROM authorization_source WHERE employee_id=? AND company_id=? AND department_id=? AND system_id=?",
            (r,n)->new Source(r.getString("id"),key,r.getLong("source_epoch"),r.getLong("source_version")),key.employeeId(),key.companyId(),key.departmentId(),key.systemId());
        return rows.isEmpty()?new Source(null,key,0,0):rows.getFirst();
    }
    public Baseline baseline(Key key){
        transaction();Role role=role(key.roleId());if(!role.companyId().equals(key.sourceCompanyId()))throw IdentityFailure.invalid();
        Source source=source(new SourceKey(key.employeeId(),key.sourceCompanyId(),key.sourceDepartmentId(),role.systemId()));
        String baseline=null,active=null;long relation=0;
        if(source.id()!=null){
            var rows=jdbc.queryForList("SELECT id,relation_version FROM grant_baseline WHERE source_id=? AND role_id=?",source.id(),role.id());
            if(!rows.isEmpty()){
                baseline=(String)rows.getFirst().get("id");relation=((Number)rows.getFirst().get("relation_version")).longValue();
                var grants=jdbc.queryForList("SELECT id FROM role_grant WHERE baseline_id=? AND status='ACTIVE'",String.class,baseline);
                if(!grants.isEmpty())active=grants.getFirst();
            }
        }
        boolean applicable=role.departmentId()==null || role.departmentId().equals(key.sourceDepartmentId());
        var jobs=applicable?people.applicableEmployments(UUID.fromString(key.employeeId()),UUID.fromString(key.sourceCompanyId()),UUID.fromString(key.sourceDepartmentId())):List.<OrganizationReader.Employment>of();
        return new Baseline(baseline,key,role,source,relation,active,jobs,role.systemEnabled() && role.state().equals("EFFECTIVE") && role.configurationVersion()>0 && !jobs.isEmpty());
    }
    public Optional<Grant> grant(String id){
        transaction();
        var rows=jdbc.query("SELECT g.id,s.employee_id,b.role_id,s.company_id,s.department_id,s.system_id,g.source_epoch,g.relation_version,g.status,g.ended_reason,g.created_at FROM role_grant g JOIN grant_baseline b ON b.id=g.baseline_id JOIN authorization_source s ON s.id=b.source_id WHERE g.id=?",(r,n)->{
            String employee=r.getString("employee_id"),company=r.getString("company_id"),department=r.getString("department_id");
            var jobs=jdbc.query("SELECT e.id,e.employee_id,e.company_id,e.department_id FROM grant_employment_evidence ge JOIN employment e ON e.id=ge.employment_id WHERE ge.grant_id=? ORDER BY e.id",(e,i)->{
                if(!employee.equals(e.getString("employee_id")) || !company.equals(e.getString("company_id")) || !department.equals(e.getString("department_id")))throw new PersistenceFailure(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR,null);
                return e.getString("id");
            },r.getString("id"));
            if(r.getString("status").equals("ACTIVE") && jobs.isEmpty())throw new PersistenceFailure(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR,null);
            return new Grant(r.getString("id"),r.getString("employee_id"),r.getString("role_id"),r.getString("company_id"),r.getString("department_id"),r.getString("system_id"),jobs,r.getLong("source_epoch"),r.getLong("relation_version"),r.getString("status"),r.getString("ended_reason"),r.getTimestamp("created_at",Calendar.getInstance(TimeZone.getTimeZone("UTC"))).toInstant());
        },id);return rows.stream().findFirst();
    }
    private boolean exists(String sql,Object...args){return !jdbc.queryForList(sql,args).isEmpty();}
    private void transaction(){if(!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(dataSource))throw new IllegalStateException("GrantReader requires the caller transaction on its own datasource");}
    private static IdentityFailure missing(){return new IdentityFailure(404,"NOT_FOUND");}
}
