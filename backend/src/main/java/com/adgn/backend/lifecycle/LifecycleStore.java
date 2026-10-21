package com.adgn.backend.lifecycle;

import com.adgn.backend.core.transaction.WriteContext;
import com.adgn.backend.identity.repository.IdentityRepository;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.people.PeopleQueries;
import com.adgn.backend.web.scope.dto.ScopeResponses;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.*;

/** SQL facts shared by lifecycle commands; never reads hashes, tokens or private keys. */
final class LifecycleStore {
    final DataSource source;
    final JdbcTemplate jdbc;
    private final IdentityRepository identities;
    LifecycleStore(DataSource source,IdentityRepository identities){this.source=source;this.jdbc=new JdbcTemplate(source);this.identities=identities;}
    static long number(Map<String,Object> r,String key){return ((Number)r.get(key)).longValue();}
    static boolean flag(Object value){return Boolean.TRUE.equals(value)||value instanceof Number n&&n.intValue()!=0;}
    static IdentityFailure conflict(){return new IdentityFailure(409,"STATE_CONFLICT");}
    static void advance(long value){if(value>=9007199254740991L)throw conflict();}
    Map<String,Object> one(String sql,Object...args){var rows=jdbc.queryForList(sql,args);if(rows.isEmpty())throw new IdentityFailure(404,"NOT_FOUND");return rows.getFirst();}
    void requireGlobal(WriteContext<?> c){c.requireActive(source);if(c.actor().kind()!=com.adgn.backend.core.audit.AuditRecord.ActorKind.ACCOUNT)throw new IdentityFailure(403,"FORBIDDEN");var a=identities.byId(c.actor().id());if(a==null||!a.qualified()||!a.superAdmin())throw new IdentityFailure(403,"FORBIDDEN");}
    Map<String,Object> employee(String id){return one("SELECT id,employee_key,name,status,version FROM employee WHERE id=?",id);}
    PeopleQueries.Employee employeeView(String id){return jdbc.queryForObject("SELECT id,employee_key,name,status,version,created_at FROM employee WHERE id=?",(r,n)->new PeopleQueries.Employee(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getLong(5),r.getTimestamp(6,utc()).toInstant()),id);}
    Map<String,Object> scope(String id){return one("SELECT id,company_id,department_id,scope_version,row_version FROM management_scope WHERE id=?",id);}
    List<String> systems(String scope){return jdbc.queryForList("SELECT system_id FROM scope_system WHERE scope_id=? ORDER BY system_id",String.class,scope);}
    List<Map<String,Object>> appointments(String scope){return jdbc.queryForList("SELECT id,account_id,appointment_version,row_version FROM admin_appointment WHERE scope_id=? AND status='ACTIVE' ORDER BY id",scope);}
    ScopeResponses.Scope scopeView(String id){return jdbc.queryForObject("SELECT id,company_id,department_id,scope_version,created_at FROM management_scope WHERE id=?",(r,n)->new ScopeResponses.Scope(r.getString(1),r.getString(2),r.getString(3),systems(id),r.getLong(4),r.getTimestamp(5,utc()).toInstant()),id);}
    boolean eligible(String account,String company,String department){
        return jdbc.queryForObject("SELECT COUNT(*) FROM admin_account a JOIN employee e ON e.id=a.employee_id WHERE a.id=? AND a.enabled=1 AND a.is_super_admin=0 AND e.status='ACTIVE' AND EXISTS(SELECT 1 FROM employment em WHERE em.employee_id=e.id AND em.company_id=? AND em.department_id=? AND em.status='ACTIVE')",Long.class,account,company,department)>0;
    }
    void endAppointment(WriteContext<?> c,Map<String,Object> ap,String status,String reason){
        advance(number(ap,"appointment_version"));advance(number(ap,"row_version"));
        if(jdbc.update("UPDATE admin_appointment SET status=?,appointment_version=appointment_version+1,row_version=row_version+1,ended_at=?,ended_reason=? WHERE id=? AND status='ACTIVE' AND appointment_version=?",status,Timestamp.from(c.now()),reason,ap.get("id"),ap.get("appointment_version"))!=1)throw conflict();
    }
    void revokeSessions(WriteContext<?> c,String account){
        var a=one("SELECT session_epoch,row_version FROM admin_account WHERE id=?",account);advance(number(a,"session_epoch"));advance(number(a,"row_version"));
        jdbc.update("UPDATE admin_account SET session_epoch=session_epoch+1,row_version=row_version+1 WHERE id=?",account);
        jdbc.update("UPDATE admin_session SET revoked_at=? WHERE account_id=? AND revoked_at IS NULL",Timestamp.from(c.now()),account);
    }
    boolean qualified(String account){var a=identities.byId(account);return a!=null&&a.qualified();}
    void preventSelfLock(WriteContext<?> c,String employee){
        var actor=identities.byId(c.actor().id());
        if(employee.equals(actor.employeeId()))throw conflict();
        long target=jdbc.queryForObject("SELECT COUNT(*) FROM admin_account WHERE employee_id=? AND enabled=1 AND is_super_admin=1",Long.class,employee);
        long other=jdbc.queryForObject("SELECT COUNT(*) FROM admin_account a LEFT JOIN employee e ON e.id=a.employee_id WHERE a.enabled=1 AND a.is_super_admin=1 AND (a.employee_id IS NULL OR (a.employee_id<>? AND e.status='ACTIVE'))",Long.class,employee);
        if(target>0&&other==0)throw conflict();
    }
    Map<String,Object> employeeDependencies(String id){
        var result=new TreeMap<String,Object>();result.put("employee",employee(id));
        result.put("sources",jdbc.queryForList("SELECT id,employee_id,company_id,department_id,system_id,source_epoch,source_version,row_version FROM authorization_source WHERE employee_id=? ORDER BY id",id));
        result.put("baselines",jdbc.queryForList("SELECT b.id,b.role_id,b.relation_version,b.row_version FROM grant_baseline b JOIN authorization_source s ON s.id=b.source_id WHERE s.employee_id=? ORDER BY b.id",id));
        result.put("grants",jdbc.queryForList("SELECT g.id,g.status,g.relation_version FROM role_grant g JOIN grant_baseline b ON b.id=g.baseline_id JOIN authorization_source s ON s.id=b.source_id WHERE s.employee_id=? ORDER BY g.id",id));
        result.put("accounts",jdbc.queryForList("SELECT id,enabled,is_super_admin,session_epoch,row_version FROM admin_account WHERE employee_id=? ORDER BY id",id));
        result.put("appointments",jdbc.queryForList("SELECT ap.id,ap.scope_id,ap.status,ap.appointment_version,ap.row_version FROM admin_appointment ap JOIN admin_account a ON a.id=ap.account_id WHERE a.employee_id=? ORDER BY ap.id",id));
        result.put("sessions",jdbc.queryForList("SELECT se.id,se.revoked_at FROM admin_session se JOIN admin_account a ON a.id=se.account_id WHERE a.employee_id=? ORDER BY se.id",id));
        result.put("requests",jdbc.queryForList("SELECT r.id,r.state,r.status_version,r.content FROM change_request r WHERE r.submitted_version IS NOT NULL AND r.state IN ('PENDING','APPROVED','EXECUTION_FAILED') AND EXISTS(SELECT 1 FROM change_target t WHERE t.request_id=r.id AND t.employee_id=?) ORDER BY r.id",id));
        result.put("superAdmins",jdbc.queryForList("SELECT a.id,a.employee_id,a.enabled,e.status FROM admin_account a LEFT JOIN employee e ON e.id=a.employee_id WHERE a.is_super_admin=1 ORDER BY a.id"));
        return result;
    }
    Map<String,Object> scopeDependencies(String id,LifecycleInput.ScopeEdit in){
        var old=scope(id);var oldSystems=systems(id);var appointments=appointments(id);
        var dep=one("SELECT company_id,version FROM department WHERE id=?",in.departmentId());
        if(!in.companyId().equals(dep.get("company_id")))throw IdentityFailure.invalid();
        var systemFacts=new ArrayList<Map<String,Object>>();
        for(String system:in.systemIds()){
            var fact=one("SELECT id,enabled,version FROM business_system WHERE id=?",system);
            if(!oldSystems.contains(system)&&!flag(fact.get("enabled")))throw IdentityFailure.invalid();systemFacts.add(fact);
        }
        var accountFacts=new ArrayList<Map<String,Object>>();
        for(String account:in.accountIds()){
            if(!eligible(account,in.companyId(),in.departmentId()))throw IdentityFailure.invalid();
            accountFacts.add(one("SELECT a.id,a.version,a.session_epoch,a.row_version,e.id AS employee_id,e.status,e.version AS employee_version FROM admin_account a JOIN employee e ON e.id=a.employee_id WHERE a.id=?",account));
            accountFacts.add(Map.of("jobs",jdbc.queryForList("SELECT em.id,em.version FROM employment em JOIN admin_account a ON a.employee_id=em.employee_id WHERE a.id=? AND em.company_id=? AND em.department_id=? AND em.status='ACTIVE' ORDER BY em.id",account,in.companyId(),in.departmentId())));
        }
        var affectedAccounts=jdbc.queryForList("SELECT a.id,a.enabled,a.session_epoch,a.row_version,e.status FROM admin_account a LEFT JOIN employee e ON e.id=a.employee_id WHERE EXISTS(SELECT 1 FROM admin_appointment ap WHERE ap.scope_id=? AND ap.account_id=a.id AND ap.status='ACTIVE') ORDER BY a.id",id);
        var result=new TreeMap<String,Object>();result.put("scope",old);result.put("oldSystems",oldSystems);result.put("appointments",appointments);result.put("department",dep);result.put("systems",systemFacts);result.put("accounts",accountFacts);result.put("affectedAccounts",affectedAccounts);
        result.put("qualifications",jdbc.queryForList("SELECT ap.id,ap.account_id,ap.status,ap.appointment_version,sc.id AS scope_id,sc.company_id,sc.department_id FROM admin_appointment ap JOIN management_scope sc ON sc.id=ap.scope_id WHERE ap.account_id IN (SELECT ap2.account_id FROM admin_appointment ap2 WHERE ap2.scope_id=? AND ap2.status='ACTIVE') ORDER BY ap.id",id));
        result.put("employments",jdbc.queryForList("SELECT em.id,em.employee_id,em.company_id,em.department_id,em.status,em.version FROM employment em WHERE EXISTS(SELECT 1 FROM admin_account a JOIN admin_appointment ap ON ap.account_id=a.id WHERE ap.scope_id=? AND ap.status='ACTIVE' AND a.employee_id=em.employee_id) ORDER BY em.id",id));
        result.put("requests",jdbc.queryForList("SELECT id,state,status_version,content FROM change_request WHERE scope_id=? AND submitted_version IS NOT NULL AND state IN ('PENDING','APPROVED','EXECUTION_FAILED') ORDER BY id",id));
        return result;
    }
    static Calendar utc(){return Calendar.getInstance(TimeZone.getTimeZone("UTC"));}
}
