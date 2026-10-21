package com.adgn.backend.lifecycle;

import com.adgn.backend.core.audit.*;
import com.adgn.backend.core.transaction.WriteContext;
import com.adgn.backend.grant.*;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.people.PeopleQueries;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import static com.adgn.backend.lifecycle.LifecycleStore.*;

/** BE-30 may reuse this port inside its single guarded batch, never per-row commits. */
public final class EmployeeStateWriter {
    public record Result(PeopleQueries.Employee employee,String outcome,long invalidatedGrantCount,long invalidatedAppointmentCount,List<String> auditEventIds) { }
    private final LifecycleStore db;
    private final SourceCloser closer;
    private final RequestInvalidator invalidator;
    private final AuditAppender audit;
    private final JsonMapper json;
    EmployeeStateWriter(LifecycleStore db,SourceCloser closer,RequestInvalidator invalidator,AuditAppender audit,JsonMapper json){this.db=db;this.closer=closer;this.invalidator=invalidator;this.audit=audit;this.json=json;}
    public Result apply(WriteContext<?> c,String id,LifecycleInput.EmployeeState raw,String requestId){
        c.requireActive(db.source);
        try{
            db.requireGlobal(c);var in=raw.normalized();var old=db.employee(id);
            if(number(old,"version")!=in.expectedVersion())throw new IdentityFailure(409,"VERSION_CONFLICT");
            if(in.status().equals(old.get("status")))return new Result(db.employeeView(id),"NO_CHANGE",0,0,List.of());
            advance(number(old,"version"));
            if(in.status().equals("INACTIVE"))db.preventSelfLock(c,id);
            db.jdbc.update("UPDATE employee SET status=?,version=version+1 WHERE id=? AND version=?",in.status(),id,in.expectedVersion());
            long grantCount=0,appointmentCount=0;var invalidated=List.<RequestInvalidator.Invalidated>of();
            var facts=new AffectedFacts(List.of(),List.of());var endedAppointments=List.<Map<String,Object>>of();
            if(in.status().equals("INACTIVE")){
                var sources=db.jdbc.query("SELECT employee_id,company_id,department_id,system_id,source_epoch,source_version FROM authorization_source WHERE employee_id=? ORDER BY id",(r,n)->new SourceCloser.Selection(new GrantReader.SourceKey(r.getString(1),r.getString(2),r.getString(3),r.getString(4)),r.getLong(5),r.getLong(6)),id);
                facts=sources.isEmpty()?new AffectedFacts(List.of(),List.of()):closer.close(c,sources,in.reason());
                grantCount=facts.relations().stream().filter(r->r.grantId()!=null).count();
                var aps=db.jdbc.queryForList("SELECT ap.id,ap.appointment_version,ap.row_version FROM admin_appointment ap JOIN admin_account a ON a.id=ap.account_id WHERE a.employee_id=? AND ap.status='ACTIVE' ORDER BY ap.id",id);
                for(var ap:aps)db.endAppointment(c,ap,"INVALIDATED",in.reason());appointmentCount=aps.size();endedAppointments=aps;
                for(String account:db.jdbc.queryForList("SELECT id FROM admin_account WHERE employee_id=? ORDER BY id",String.class,id))db.revokeSessions(c,account);
                invalidated=invalidator.invalidate(c,new RequestInvalidator.Changes(facts,Set.of(id),Set.of(),Set.of(),Map.of()),requestId,in.reason());
            }
            var view=db.employeeView(id);
            var subjects=new ArrayList<AuditRecord.Subject>();subjects.add(new AuditRecord.Subject("EMPLOYEE",id));
            facts.sources().forEach(s->subjects.add(new AuditRecord.Subject("SOURCE",s.after().id())));
            facts.relations().stream().filter(r->r.grantId()!=null).forEach(r->subjects.add(new AuditRecord.Subject("GRANT",r.grantId())));
            endedAppointments.forEach(ap->subjects.add(new AuditRecord.Subject("APPOINTMENT",(String)ap.get("id"))));
            String event=audit.append(c,new AuditRecord("EMPLOYEE_STATE_CHANGED",id,null,null,new AuditRecord.Scope(AuditRecord.Visibility.GLOBAL,null,null,null),"SUCCEEDED",requestId,in.reason(),json.writeValueAsString(Map.of("schemaVersion",1,"before",old,"employee",view,"invalidatedGrantCount",grantCount,"invalidatedAppointmentCount",appointmentCount,"invalidatedRequestIds",invalidated.stream().map(RequestInvalidator.Invalidated::requestId).toList(),"grantFacts",facts,"endedAppointments",endedAppointments)),subjects)).toString();
            return new Result(view,"APPLIED",grantCount,appointmentCount,List.of(event));
        }catch(GrantMutationFailure failure){
            var conflict=new IdentityFailure(409,"VERSION_EXHAUSTED".equals(failure.code())?"STATE_CONFLICT":"VERSION_CONFLICT");
            c.markFailed(conflict);throw conflict;
        }catch(RuntimeException failure){c.markFailed(failure);throw failure;}
    }
}
