package com.adgn.backend.execution;

import com.adgn.backend.change.*;
import com.adgn.backend.core.transaction.*;
import com.adgn.backend.grant.*;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.query.change.ChangeQueryService;
import com.adgn.backend.query.management.ManagementInput;
import com.adgn.backend.role.repository.RoleRepository;
import com.adgn.backend.role.service.RoleConfigurationApplier;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.util.*;

/** 一申请一成功事务；失败记录在原业务回滚后重新取得guard。 */
public final class ExecutionService {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(ExecutionService.class);
    private final JdbcTemplate jdbc;private final CommandBoundary writes;private final FixedRequestStore fixed;private final DraftRepository drafts;private final GrantReader grants;private final GrantApplier grantApplier;private final RoleRepository roles;private final RoleConfigurationApplier configuration;private final DraftPlanner planner;private final ClaimService claims;private final WorkflowStateWriter states;private final JsonMapper json;
    private static final class Invalid extends RuntimeException{final String code;Invalid(String code){this.code=code;}}
    public ExecutionService(DataSource ds,CommandBoundary writes,FixedRequestStore fixed,DraftRepository drafts,GrantReader grants,GrantApplier grantApplier,RoleRepository roles,RoleConfigurationApplier configuration,DraftPlanner planner,ClaimService claims,WorkflowStateWriter states,JsonMapper json){jdbc=new JdbcTemplate(ds);this.writes=writes;this.fixed=fixed;this.drafts=drafts;this.grants=grants;this.grantApplier=grantApplier;this.roles=roles;this.configuration=configuration;this.planner=planner;this.claims=claims;this.states=states;this.json=json;}
    public void scan()throws Exception{for(String id:jdbc.queryForList("SELECT id FROM change_request WHERE state='APPROVED' OR (state='EXECUTION_FAILED' AND next_retry_at<=UTC_TIMESTAMP(6)) ORDER BY created_at,id LIMIT 100",String.class))execute(id);}
    public void execute(String id)throws Exception{
        ManagementInput.identifier(id);
        try{writes.execute(FixedRequestStore::worker,c->{
            if(success(id)||!runnable(id))return null;var f=fixed.load(id);
            try{preflight(f);}catch(Invalid invalid){invalidate(c,f,invalid.code);return null;}
            Object result;
            if(f.content().path("kind").stringValue().equals("ROLE_CONFIGURATION")){
                String role=DraftInput.id(f.content(),"roleId");long version=DraftInput.version(f.content(),"expectedConfigurationVersion");
                var applied=configuration.apply(c,id,role,version,DraftInput.ids(f.content().path("permissionIds"),1,10000));result=Map.of("roleId",role,"configurationVersion",applied.configurationVersion());
                // M05不反向调用M07；外层对命中的旧固定授予整份失效。
                for(String other:jdbc.queryForList("SELECT DISTINCT r.id FROM change_request r JOIN change_target t ON t.request_id=r.id WHERE r.kind='ROLE_GRANT' AND r.state IN ('PENDING','APPROVED','EXECUTION_FAILED') AND t.role_id=? AND t.expected_configuration_version<>? ORDER BY r.id",String.class,role,applied.configurationVersion())){
                    var old=fixed.load(other);invalidate(c,old,"ROLE_CONFIGURATION_CHANGED");
                }
            }else result=grantApplier.apply(c,id);
            var event=fixed.event(c,f,"EXECUTED","SUCCEEDED",result);
            jdbc.update("INSERT INTO execution_success(request_id,audit_event_id,executed_at,result_snapshot) VALUES(?,?,UTC_TIMESTAMP(6),?)",id,event.toString(),json.writeValueAsString(result));
            attempt(c,id,"SUCCEEDED",null);
            if(jdbc.update("UPDATE change_request SET state='EXECUTED',execution_status='SUCCEEDED',failure_code=NULL,next_retry_at=NULL,status_version=status_version+1,row_version=row_version+1 WHERE id=? AND status_version=? AND status_version<9007199254740991 AND row_version<9007199254740991",id,f.statusVersion())!=1)throw ChangeQueryService.integrity();
            claims.release(c,id);return null;
        });}catch(Exception failure){
            // 不保留SQL、原异常文本、栈或敏感输入；批准接收事务不会回滚。
            LOG.warn("workflow_execution_failure category=STORAGE");
            try{recordFailure(id);}catch(Exception unavailable){LOG.warn("workflow_failure_record_unavailable category=STORAGE");}
        }
    }
    private boolean success(String id){return !jdbc.queryForList("SELECT 1 FROM execution_success WHERE request_id=?",id).isEmpty();}
    private boolean runnable(String id){return !jdbc.queryForList("SELECT 1 FROM change_request WHERE id=? AND (state='APPROVED' OR (state='EXECUTION_FAILED' AND next_retry_at<=UTC_TIMESTAMP(6)))",id).isEmpty();}
    private void preflight(FixedRequestStore.Fixed f){
        if(!f.delivery().equals("LINKED")||!f.approval().equals("APPROVED")||jdbc.queryForObject("SELECT COUNT(*) FROM oa_link l JOIN oa_final_result o ON o.request_id=l.request_id AND o.decision='APPROVED' WHERE l.request_id=?",Long.class,f.id())!=1)throw ChangeQueryService.integrity();
        var ts=drafts.targets(f.id());boolean config=f.content().path("kind").stringValue().equals("ROLE_CONFIGURATION"),grant=f.content().path("kind").stringValue().equals("ROLE_GRANT");
        for(var t:ts){
            var role=roles.get(t.role());if(role==null)throw ChangeQueryService.integrity();
            if(f.scopeId()!=null){
                if(t.department()==null||jdbc.queryForObject("SELECT COUNT(*) FROM management_scope s JOIN scope_system ss ON ss.scope_id=s.id WHERE s.id=? AND s.company_id=? AND s.department_id=? AND ss.system_id=?",Long.class,f.scopeId(),t.company(),t.department(),t.system())!=1||role.departmentId()!=null&&!role.departmentId().equals(t.department())||config&&role.departmentId()==null)throw new Invalid("SCOPE_NO_LONGER_COVERS");
            }
            if(config){
                if(jdbc.queryForObject("SELECT COUNT(*) FROM role_change_claim WHERE role_id=? AND request_id=?",Long.class,t.role(),f.id())!=1)throw ChangeQueryService.integrity();
                if(role.configurationVersion()!=t.configurationVersion())throw new Invalid("ROLE_CONFIGURATION_CHANGED");
                try{planner.validatePermissions(role,DraftInput.ids(f.content().path("permissionIds"),1,10000));}catch(IdentityFailure unavailable){if(unavailable.status()==422)throw new Invalid("PERMISSION_UNAVAILABLE");throw unavailable;}
            }else{
                var b=grants.baseline(new GrantReader.Key(t.employee(),t.role(),t.company(),t.department()));
                if(!Objects.equals(t.source(),b.source().id())||!Objects.equals(t.baseline(),b.id())||jdbc.queryForObject("SELECT COUNT(*) FROM relation_change_claim WHERE baseline_id=? AND request_id=?",Long.class,t.baseline(),f.id())!=1)throw ChangeQueryService.integrity();
                if(grant){
                    if(b.source().sourceEpoch()!=t.sourceEpoch())throw new Invalid("SOURCE_EPOCH_CHANGED");
                    if(b.role().configurationVersion()!=t.configurationVersion())throw new Invalid("ROLE_CONFIGURATION_CHANGED");
                }
                if(b.relationVersion()!=t.relationVersion())throw new Invalid("RELATION_VERSION_CHANGED");
                if(grant){
                    if(b.activeGrantId()!=null)throw new Invalid("RELATION_VERSION_CHANGED");
                    if(!"ACTIVE".equals(jdbc.queryForObject("SELECT status FROM employee WHERE id=?",String.class,t.employee())))throw new Invalid("EMPLOYEE_INACTIVE");
                    if(!b.eligibleForGrant())throw new Invalid(b.employments().isEmpty()?"NO_APPLICABLE_EMPLOYMENT":"PERMISSION_UNAVAILABLE");
                }else if(!Objects.equals(t.grant(),b.activeGrantId()))throw new Invalid("RELATION_VERSION_CHANGED");
            }
        }
    }
    private void invalidate(WriteContext<?> c,FixedRequestStore.Fixed f,String code){if(states.invalidate(c,f.id(),f.statusVersion(),code)){fixed.event(c,f,"INVALIDATED","INVALIDATED",Map.of("failureCode",code));attempt(c,f.id(),"INVALIDATED",code);}}
    private void recordFailure(String id)throws Exception{writes.execute(FixedRequestStore::worker,c->{
        if(success(id))return null;var rows=jdbc.queryForList("SELECT state FROM change_request WHERE id=?",String.class,id);if(rows.isEmpty()||!Set.of("APPROVED","EXECUTION_FAILED").contains(rows.getFirst()))return null;
        var f=fixed.load(id);try{preflight(f);}catch(Invalid invalid){invalidate(c,f,invalid.code);return null;}
        int n=jdbc.queryForObject("SELECT COALESCE(MAX(attempt_no),0)+1 FROM execution_attempt WHERE request_id=?",Integer.class,id);long delay=Math.min(300,5L<<Math.min(6,n-1));
        if(jdbc.update("UPDATE change_request SET state='EXECUTION_FAILED',execution_status='FAILED',failure_code='EXECUTION_STORAGE_FAILURE',next_retry_at=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)),status_version=status_version+1,row_version=row_version+1 WHERE id=? AND status_version=? AND status_version<9007199254740991 AND row_version<9007199254740991",delay,id,f.statusVersion())!=1)throw ChangeQueryService.integrity();
        attempt(c,id,"FAILED","EXECUTION_STORAGE_FAILURE");fixed.event(c,f,"EXECUTION_FAILED","FAILED",Map.of("failureCode","EXECUTION_STORAGE_FAILURE"));return null;
    });}
    private void attempt(WriteContext<?> c,String id,String outcome,String code){int next=jdbc.queryForObject("SELECT COALESCE(MAX(attempt_no),0)+1 FROM execution_attempt WHERE request_id=?",Integer.class,id);jdbc.update("INSERT INTO execution_attempt(id,request_id,attempt_no,outcome,failure_code,started_at,finished_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",c.newId().toString(),id,next,outcome,code);}
}
