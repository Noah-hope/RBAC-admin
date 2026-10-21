package com.adgn.backend.imports;

import com.adgn.backend.core.audit.*;
import com.adgn.backend.core.transaction.WriteContext;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.people.EmploymentWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.util.*;
import static com.adgn.backend.imports.ImportPlan.*;

/** Guarded continuation after a failed or uncertain commit. A known success always wins. */
public final class ImportFailureRecorder {
    private final DataSource source;private final JdbcTemplate jdbc;private final ImportPlanService reports;private final AuditAppender audit;private final JsonMapper json;
    public ImportFailureRecorder(DataSource source,ImportPlanService reports,AuditAppender audit,JsonMapper json){this.source=source;this.jdbc=new JdbcTemplate(source);this.reports=reports;this.audit=audit;this.json=json;}
    public Batch record(WriteContext<?> c,String state){
        c.requireActive(source);
        try{
            if(!(c.authority() instanceof EmploymentWriter.ImportAuthority a)||c.actor().kind()!=AuditRecord.ActorKind.ACCOUNT||!c.actor().id().equals(a.accountId().toString()))throw new IdentityFailure(403,"FORBIDDEN");
            if(jdbc.queryForObject("SELECT COUNT(*) FROM admin_account a LEFT JOIN employee e ON e.id=a.employee_id WHERE a.id=? AND a.enabled=1 AND a.is_super_admin=1 AND a.must_change_password=0 AND (a.employee_id IS NULL OR e.status='ACTIVE')",Long.class,a.accountId().toString())!=1)throw new IdentityFailure(403,"FORBIDDEN");
            if(!Set.of("STALE","FAILED").contains(state))throw IdentityFailure.invalid();
            var live=reports.load(a.batchId().toString());
            if(live.state().equals("SUCCEEDED")||!Set.of("READY","FAILED").contains(live.state())||live.version()!=a.batchVersion()||!live.contentDigest().equals(a.contentDigest())||live.version()==9007199254740991L)return live;
            String code=state.equals("STALE")?"PREVIEW_STALE":"DEPENDENCY_UNAVAILABLE";
            if(jdbc.update("UPDATE import_batch SET state=?,version=version+1,row_version=row_version+1,failure_code=? WHERE id=? AND version=? AND state IN ('READY','FAILED') AND row_version<9007199254740991",state,code,live.id(),live.version())!=1)throw new IdentityFailure(409,"STATE_CONFLICT");
            audit.append(c,new AuditRecord("IMPORT_"+state,live.id(),null,null,new AuditRecord.Scope(AuditRecord.Visibility.GLOBAL,null,null,null),"FAILED",a.requestId(),live.input().reason(),json.writeValueAsString(Map.of("schemaVersion",1,"batchId",live.id(),"failureCode",code)),List.of(new AuditRecord.Subject("IMPORT_BATCH",live.id()))));return reports.load(live.id());
        }catch(RuntimeException failure){c.markFailed(failure);throw failure;}
    }
}
