package com.adgn.backend.lifecycle;

import com.adgn.backend.core.audit.*;
import com.adgn.backend.core.transaction.WriteContext;
import com.adgn.backend.grant.*;
import com.adgn.backend.people.EmploymentWriter;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

/** Import event hook: apply immediately after each ended employment in the caller's batch. */
public final class EmploymentLifecycleWriter {
    public record Result(long invalidatedGrants,long invalidatedAppointments) { }
    private final LifecycleStore db;private final SourceCloser closer;private final RequestInvalidator invalidator;private final AuditAppender audit;private final JsonMapper json;
    EmploymentLifecycleWriter(LifecycleStore db,SourceCloser closer,RequestInvalidator invalidator,AuditAppender audit,JsonMapper json){this.db=db;this.closer=closer;this.invalidator=invalidator;this.audit=audit;this.json=json;}
    public Result ended(WriteContext<?> c,EmploymentWriter.EndResult ended,String reason,String correlation){
        c.requireActive(db.source);
        try{
            db.requireGlobal(c);String employee=ended.employeeId().toString(),company=ended.companyId().toString(),department=ended.departmentId().toString();
            var facts=new AffectedFacts(List.of(),List.of());
            if(!employed(employee,company,department)){
                var selected=db.jdbc.query("SELECT system_id,source_epoch,source_version FROM authorization_source WHERE employee_id=? AND company_id=? AND department_id=? ORDER BY id",(r,n)->new SourceCloser.Selection(new GrantReader.SourceKey(employee,company,department,r.getString(1)),r.getLong(2),r.getLong(3)),employee,company,department);
                if(!selected.isEmpty())facts=closer.close(c,selected,reason);
                var relations=db.jdbc.query("SELECT r.id,s.department_id,b.relation_version FROM role_grant g JOIN grant_baseline b ON b.id=g.baseline_id JOIN authorization_source s ON s.id=b.source_id JOIN business_role r ON r.id=b.role_id WHERE s.employee_id=? AND r.company_id=? AND r.department_id=? AND g.status='ACTIVE' ORDER BY b.id",(r,n)->new SourceCloser.RelationSelection(new GrantReader.Key(employee,r.getString(1),company,r.getString(2)),r.getLong(3)),employee,company,department);
                if(!relations.isEmpty()){var extra=closer.invalidateRelations(c,relations,reason);var all=new ArrayList<>(facts.relations());all.addAll(extra.relations());facts=new AffectedFacts(facts.sources(),all);}
            }
            var appointments=db.jdbc.queryForList("SELECT ap.id,ap.account_id,ap.appointment_version,ap.row_version FROM admin_appointment ap JOIN admin_account a ON a.id=ap.account_id JOIN management_scope sc ON sc.id=ap.scope_id WHERE a.employee_id=? AND ap.status='ACTIVE' AND sc.company_id=? AND sc.department_id=? ORDER BY ap.id",employee,company,department);
            long appointmentCount=0;var accounts=new HashSet<String>();var endedIds=new ArrayList<String>();
            if(!employed(employee,company,department))for(var ap:appointments){db.endAppointment(c,ap,"INVALIDATED",reason);accounts.add((String)ap.get("account_id"));endedIds.add((String)ap.get("id"));appointmentCount++;}
            for(String account:accounts)if(!db.qualified(account))db.revokeSessions(c,account);
            var invalidated=invalidator.invalidate(c,new RequestInvalidator.Changes(facts,Set.of(),Set.of(employee),Set.of(),Map.of()),correlation,reason);
            var subjects=new ArrayList<AuditRecord.Subject>();subjects.add(new AuditRecord.Subject("EMPLOYEE",employee));subjects.add(new AuditRecord.Subject("EMPLOYMENT",ended.id().toString()));facts.sources().forEach(s->subjects.add(new AuditRecord.Subject("SOURCE",s.after().id())));facts.relations().stream().filter(r->r.grantId()!=null).forEach(r->subjects.add(new AuditRecord.Subject("GRANT",r.grantId())));endedIds.forEach(id->subjects.add(new AuditRecord.Subject("APPOINTMENT",id)));
            audit.append(c,new AuditRecord("IMPORT_EMPLOYMENT_LIFECYCLE",ended.id().toString(),null,null,new AuditRecord.Scope(AuditRecord.Visibility.GLOBAL,ended.companyId(),ended.departmentId(),null),"SUCCEEDED",correlation,reason,json.writeValueAsString(Map.of("schemaVersion",1,"employmentId",ended.id(),"grantFacts",facts,"invalidatedAppointmentIds",endedIds,"invalidatedRequestIds",invalidated.stream().map(RequestInvalidator.Invalidated::requestId).toList())),subjects));
            return new Result(facts.relations().stream().filter(r->r.grantId()!=null).count(),appointmentCount);
        }catch(RuntimeException failure){c.markFailed(failure);throw failure;}
    }
    private boolean employed(String employee,String company,String department){return db.jdbc.queryForObject("SELECT COUNT(*) FROM employment em JOIN employee e ON e.id=em.employee_id WHERE em.employee_id=? AND em.company_id=? AND em.department_id=? AND em.status='ACTIVE' AND e.status='ACTIVE'",Long.class,employee,company,department)>0;}
}
