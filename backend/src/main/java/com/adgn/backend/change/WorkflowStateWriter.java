package com.adgn.backend.change;

import com.adgn.backend.core.transaction.WriteContext;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.query.management.ManagementInput;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.*;

/** 调用方负责选择命中申请、权限、安全统计及审计；本端口只维护状态不变量。 */
public final class WorkflowStateWriter {
    public record State(String id,String state,long statusVersion,boolean submitted) { }
    private final DataSource source;private final JdbcTemplate jdbc;private final ClaimService claims;
    public WorkflowStateWriter(DataSource source,ClaimService claims){this.source=source;this.jdbc=new JdbcTemplate(source);this.claims=claims;}
    public State read(WriteContext<?> c,String request){
        Objects.requireNonNull(c,"需要调用方的guard事务").requireActive(source);ManagementInput.identifier(request);
        var rows=jdbc.query("SELECT id,state,status_version,submitted_version FROM change_request WHERE id=?",(r,n)->new State(r.getString("id"),r.getString("state"),r.getLong("status_version"),r.getObject("submitted_version")!=null),request);
        if(rows.isEmpty())throw new IdentityFailure(404,"NOT_FOUND");return rows.getFirst();
    }
    public boolean invalidate(WriteContext<?> c,String request,long expectedStatusVersion,String reasonCode){
        Objects.requireNonNull(c,"需要调用方的guard事务").requireActive(source);
        try{
            ManagementInput.version(expectedStatusVersion);
            if(reasonCode==null||!reasonCode.matches("[A-Z][A-Z0-9_]{0,63}"))throw IdentityFailure.invalid();
            var state=read(c,request);
            if(!state.submitted()||!Set.of("PENDING","APPROVED","EXECUTION_FAILED").contains(state.state()))return false;
            if(state.statusVersion()!=expectedStatusVersion)throw new IdentityFailure(409,"VERSION_CONFLICT");
            if(expectedStatusVersion>=ManagementInput.MAX_VERSION)throw new IdentityFailure(409,"STATE_CONFLICT");
            int changed=jdbc.update("UPDATE change_request SET state='INVALIDATED',execution_status='INVALIDATED',failure_code=?,next_retry_at=NULL,status_version=status_version+1,row_version=row_version+1 WHERE id=? AND status_version=? AND row_version<?",reasonCode,request,expectedStatusVersion,ManagementInput.MAX_VERSION);
            if(changed!=1)throw new IdentityFailure(409,"STATE_CONFLICT");
            if(!jdbc.queryForList("SELECT 1 FROM oa_outbox WHERE request_id=? AND task_state IN ('READY','LEASED') AND row_version>=?",request,ManagementInput.MAX_VERSION).isEmpty())throw new IdentityFailure(409,"STATE_CONFLICT");
            jdbc.update("UPDATE oa_outbox SET task_state='STOPPED',lease_token=NULL,lease_until=NULL,row_version=row_version+1 WHERE request_id=? AND task_state IN ('READY','LEASED')",request);
            claims.release(c,request);return true;
        }catch(RuntimeException failure){c.markFailed(failure);throw failure;}
    }
}
