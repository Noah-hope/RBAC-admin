package com.adgn.backend.lifecycle;

import com.adgn.backend.core.audit.*;
import com.adgn.backend.core.transaction.WriteContext;
import com.adgn.backend.grant.AffectedFacts;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.web.scope.dto.ScopeResponses;
import tools.jackson.databind.json.JsonMapper;
import java.sql.Timestamp;
import java.util.*;
import static com.adgn.backend.lifecycle.LifecycleStore.*;

final class ScopeReplacementService {
    record Result(ScopeResponses.Scope scope,List<String> auditEventIds,String outcome,int appointmentsChangedCount) { }
    private final LifecycleStore db;private final RequestInvalidator invalidator;private final AuditAppender audit;private final JsonMapper json;
    ScopeReplacementService(LifecycleStore db,RequestInvalidator invalidator,AuditAppender audit,JsonMapper json){this.db=db;this.invalidator=invalidator;this.audit=audit;this.json=json;}
    Result apply(WriteContext<?> c,String id,LifecycleInput.ScopeEdit in,Map<String,String> bindings,String requestId){
        c.requireActive(db.source);
        try{
            db.requireGlobal(c);var old=db.scope(id);
            if(number(old,"scope_version")!=in.expectedVersion())throw new IdentityFailure(409,"VERSION_CONFLICT");
            db.scopeDependencies(id,in);var aps=db.appointments(id);var oldSystems=db.systems(id);
            var oldAccounts=aps.stream().map(a->(String)a.get("account_id")).sorted().toList();
            if(in.companyId().equals(old.get("company_id"))&&in.departmentId().equals(old.get("department_id"))&&in.systemIds().equals(oldSystems)&&in.accountIds().equals(oldAccounts))return new Result(db.scopeView(id),List.of(),"NO_CHANGE",0);
            advance(number(old,"scope_version"));advance(number(old,"row_version"));
            var beforeQualified=new TreeMap<String,Boolean>();for(String account:oldAccounts)beforeQualified.put(account,db.qualified(account));
            int changed=0;var retained=new HashSet<String>();
            for(var ap:aps){String account=(String)ap.get("account_id");
                boolean eligible=db.eligible(account,in.companyId(),in.departmentId());
                if(in.accountIds().contains(account)){if(!eligible)throw IdentityFailure.invalid();retained.add(account);}
                else {boolean moved=!in.companyId().equals(old.get("company_id"))||!in.departmentId().equals(old.get("department_id"));db.endAppointment(c,ap,moved&&!eligible?"INVALIDATED":"REVOKED",in.reason());changed++;}
            }
            db.jdbc.update("UPDATE management_scope SET company_id=?,department_id=?,scope_version=scope_version+1,row_version=row_version+1 WHERE id=?",in.companyId(),in.departmentId(),id);
            db.jdbc.update("DELETE FROM scope_system WHERE scope_id=?",id);
            for(String system:in.systemIds())db.jdbc.update("INSERT INTO scope_system(scope_id,system_id) VALUES(?,?)",id,system);
            for(String account:in.accountIds())if(!retained.contains(account)){db.jdbc.update("INSERT INTO admin_appointment(id,scope_id,account_id,created_at) VALUES(?,?,?,?)",Objects.requireNonNull(bindings.get("appointment:"+account)),id,account,Timestamp.from(c.now()));changed++;}
            for(var entry:beforeQualified.entrySet())if(entry.getValue()&&!db.qualified(entry.getKey()))db.revokeSessions(c,entry.getKey());
            var invalidated=invalidator.invalidate(c,new RequestInvalidator.Changes(new AffectedFacts(List.of(),List.of()),Set.of(),Set.of(),Set.of(id),Map.of()),requestId,in.reason());
            var view=db.scopeView(id);
            String event=audit.append(c,new AuditRecord("MANAGEMENT_SCOPE_REPLACED",id,null,null,new AuditRecord.Scope(AuditRecord.Visibility.GLOBAL,null,null,null),"SUCCEEDED",requestId,in.reason(),json.writeValueAsString(Map.of("schemaVersion",1,"before",old,"beforeSystems",oldSystems,"beforeAppointments",aps,"scope",view,"accountIds",in.accountIds(),"invalidatedRequestIds",invalidated.stream().map(RequestInvalidator.Invalidated::requestId).toList())),List.of(new AuditRecord.Subject("SCOPE",id)))).toString();
            return new Result(view,List.of(event),"APPLIED",changed);
        }catch(RuntimeException failure){c.markFailed(failure);throw failure;}
    }
}
