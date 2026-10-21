package com.adgn.backend.oa;

import com.adgn.backend.change.*;
import com.adgn.backend.core.idempotency.*;
import com.adgn.backend.core.transaction.*;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.query.change.*;
import com.adgn.backend.query.management.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.util.*;

public final class OaDeliveryService {
    private record Lease(String id,String token,String instance){}
    private final JdbcTemplate jdbc;private final CommandBoundary writes;private final FixedRequestStore fixed;private final ObjectProvider<OaSubmissionPort> adapters;private final ManagementAccess access;private final RequestVisibility visibility;private final ChangeQueryService queries;private final ObjectProvider<IdempotencyCoordinator> idem;private final JsonMapper json;
    public OaDeliveryService(DataSource ds,CommandBoundary writes,FixedRequestStore fixed,ObjectProvider<OaSubmissionPort> adapters,ManagementAccess access,RequestVisibility visibility,ChangeQueryService queries,ObjectProvider<IdempotencyCoordinator> idem,JsonMapper json){jdbc=new JdbcTemplate(ds);this.writes=writes;this.fixed=fixed;this.adapters=adapters;this.access=access;this.visibility=visibility;this.queries=queries;this.idem=idem;this.json=json;}
    public void scan()throws Exception{for(String id:jdbc.queryForList("SELECT request_id FROM oa_outbox WHERE (task_state='READY' AND next_attempt_at<=UTC_TIMESTAMP(6)) OR (task_state='LEASED' AND lease_until<=UTC_TIMESTAMP(6)) ORDER BY next_attempt_at,request_id LIMIT 100",String.class))deliver(id);}
    public boolean deliver(String id)throws Exception{
        var lease=writes.execute(FixedRequestStore::worker,c->{
            var rows=jdbc.queryForList("SELECT o.oa_instance_id FROM oa_outbox o JOIN change_request r ON r.id=o.request_id JOIN service_identity s ON s.id=o.oa_instance_id AND s.kind='OA' AND s.enabled=1 WHERE o.request_id=? AND ((o.task_state='READY' AND o.next_attempt_at<=UTC_TIMESTAMP(6)) OR (o.task_state='LEASED' AND o.lease_until<=UTC_TIMESTAMP(6))) AND o.attempt_count<2147483647 AND o.row_version<9007199254740991 AND r.state='PENDING' AND r.approval_result='NONE' AND r.delivery_status IN ('PENDING_SEND','SEND_FAILED') AND NOT EXISTS(SELECT 1 FROM oa_link l WHERE l.request_id=r.id)",String.class,id);
            if(rows.isEmpty())return null;String token=c.newId().toString();jdbc.update("UPDATE oa_outbox SET task_state='LEASED',lease_token=?,lease_until=UTC_TIMESTAMP(6)+INTERVAL 60 SECOND,attempt_count=attempt_count+1,row_version=row_version+1 WHERE request_id=?",token,id);return new Lease(id,token,rows.getFirst());});
        if(lease==null)return false;
        var submission=writes.execute(FixedRequestStore::worker,c->{if(!owns(lease)||!sendable(id))return null;var f=fixed.load(id);var payload=(tools.jackson.databind.node.ObjectNode)f.payload().deepCopy();payload.put("contentDigest",f.digest());return new OaSubmissionPort.Submission(lease.instance(),id,f.submittedVersion(),f.digest(),payload);});
        if(submission==null)return false;
        OaSubmissionPort.Receipt receipt;
        try{if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Network must run outside transaction");var adapter=adapters.getIfUnique();if(adapter==null)throw new IllegalStateException("OA adapter unavailable");receipt=adapter.send(submission);validate(submission,receipt);}
        catch(Exception unavailable){failed(lease);return false;}
        return writes.execute(FixedRequestStore::worker,c->{
            var f=fixed.load(id);var existing=jdbc.queryForList("SELECT oa_instance_id,oa_approval_id FROM oa_link WHERE request_id=?",id);
            if(!existing.isEmpty())return existing.getFirst().get("oa_instance_id").equals(lease.instance())&&existing.getFirst().get("oa_approval_id").equals(receipt.oaApprovalId());
            // 失效可能清掉在途租约；仍允许唯一匹配回执留历史，过期旧租约不能覆盖新领取者。
            if(!owns(lease)&&!f.state().equals("INVALIDATED"))return false;
            if(!f.digest().equals(receipt.contentDigest())||f.submittedVersion()!=receipt.submittedVersion())throw new IdentityFailure(409,"CALLBACK_CONFLICT");
            jdbc.update("INSERT INTO oa_link(request_id,oa_instance_id,oa_approval_id,accepted_at,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6))",ps->{ps.setString(1,id);ps.setString(2,lease.instance());ps.setString(3,receipt.oaApprovalId());ps.setTimestamp(4,java.sql.Timestamp.from(receipt.acceptedAt()),Calendar.getInstance(TimeZone.getTimeZone("UTC")));});
            jdbc.update("UPDATE change_request SET delivery_status='LINKED',status_version=status_version+1,row_version=row_version+1 WHERE id=? AND status_version<9007199254740991 AND row_version<9007199254740991",id);
            jdbc.update("UPDATE oa_outbox SET task_state=?,lease_token=NULL,lease_until=NULL,last_failure_code=NULL,row_version=row_version+1 WHERE request_id=?",f.state().equals("INVALIDATED")?"STOPPED":"DONE",id);
            fixed.event(c,f,"OA_LINKED","SUCCEEDED",Map.of("oaApprovalId",receipt.oaApprovalId()));return true;
        });
    }
    private boolean owns(Lease l){return !jdbc.queryForList("SELECT 1 FROM oa_outbox WHERE request_id=? AND task_state='LEASED' AND lease_token=? AND lease_until>UTC_TIMESTAMP(6)",l.id(),l.token()).isEmpty();}
    private boolean sendable(String id){return !jdbc.queryForList("SELECT 1 FROM change_request r JOIN oa_outbox o ON o.request_id=r.id JOIN service_identity s ON s.id=o.oa_instance_id AND s.enabled=1 WHERE r.id=? AND r.state='PENDING' AND r.delivery_status IN ('PENDING_SEND','SEND_FAILED') AND r.approval_result='NONE' AND NOT EXISTS(SELECT 1 FROM oa_link l WHERE l.request_id=r.id)",id).isEmpty();}
    private void failed(Lease l)throws Exception{writes.execute(FixedRequestStore::worker,c->{if(!owns(l)||!sendable(l.id()))return null;var f=fixed.load(l.id());int attempt=jdbc.queryForObject("SELECT attempt_count FROM oa_outbox WHERE request_id=?",Integer.class,l.id());long delay=Math.min(300,5L<<Math.min(6,attempt-1));
        jdbc.update("UPDATE oa_outbox SET task_state='READY',next_attempt_at=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)),lease_token=NULL,lease_until=NULL,last_failure_code='OA_DELIVERY_UNAVAILABLE',row_version=row_version+1 WHERE request_id=?",delay,l.id());
        jdbc.update("UPDATE change_request SET delivery_status='SEND_FAILED',status_version=status_version+1,row_version=row_version+1 WHERE id=? AND status_version<9007199254740991 AND row_version<9007199254740991",l.id());fixed.event(c,f,"OA_DELIVERY_FAILED","FAILED",Map.of("failureCode","OA_DELIVERY_UNAVAILABLE"));return null;});}
    private static void validate(OaSubmissionPort.Submission s,OaSubmissionPort.Receipt r){if(r==null||!s.requestId().equals(r.requestId())||s.submittedVersion()!=r.submittedVersion()||!s.contentDigest().equals(r.contentDigest())||r.acceptedAt()==null)throw new IllegalArgumentException();ManagementInput.identifier(r.oaApprovalId());}
    public IdempotencyCoordinator.Outcome retry(Jwt jwt,ManagementAccess.Context context,String id,String key,JsonNode input)throws Exception{
        if(input==null||!input.isObject()||!input.propertyNames().equals(Set.of("expectedStatusVersion","reason")))throw IdentityFailure.invalid();long version=DraftInput.version(input,"expectedStatusVersion");if(!input.path("reason").isString())throw IdentityFailure.invalid();String reason=ManagementInput.text(input.path("reason").stringValue(),1,500);ManagementInput.identifier(id);var coordinator=idem.getIfAvailable();if(coordinator==null)throw new IdentityFailure(503,"DEPENDENCY_UNAVAILABLE");
        return coordinator.execute(new IdempotencyRequest(UUID.fromString(jwt.getSubject()),"retryOaDelivery","POST","/api/v1/admin/change-requests/"+id+"/delivery-retries",ManagementInput.key(key),json.writeValueAsBytes(input)),()->access.read(jwt,context).identity(),(a,b)->{if(!b.operationId().equals("retryOaDelivery")||b.policy()==AuthorizationBasis.Policy.PRINCIPAL_ONLY)throw new IdentityFailure(403,"FORBIDDEN");for(var t:b.targets())if(!a.role(t.companyId().toString(),t.departmentId().toString(),t.systemId().toString(),true))throw new IdentityFailure(403,"FORBIDDEN");if(b.policy()==AuthorizationBasis.Policy.GLOBAL_ONLY&&!a.global())throw new IdentityFailure(403,"FORBIDDEN");},c->{
            visibility.require(c.authority(),id,404);var f=fixed.load(id);if(!f.state().equals("PENDING")||!f.delivery().equals("SEND_FAILED")||!f.approval().equals("NONE"))throw new IdentityFailure(409,"STATE_CONFLICT");if(f.statusVersion()!=version)throw new IdentityFailure(409,"VERSION_CONFLICT");
            if(jdbc.update("UPDATE oa_outbox SET task_state='READY',next_attempt_at=UTC_TIMESTAMP(6),last_failure_code=NULL,row_version=row_version+1 WHERE request_id=? AND task_state='READY' AND row_version<9007199254740991",id)!=1)throw new IdentityFailure(409,"STATE_CONFLICT");
            if(jdbc.update("UPDATE change_request SET delivery_status='PENDING_SEND',status_version=status_version+1,row_version=row_version+1 WHERE id=? AND status_version=? AND status_version<9007199254740991 AND row_version<9007199254740991",id,version)!=1)throw new IdentityFailure(409,"STATE_CONFLICT");
            var ts=jdbc.queryForList("SELECT company_id,department_id,system_id FROM change_target WHERE request_id=? UNION SELECT r.company_id,r.department_id,r.system_id FROM change_target t JOIN business_role r ON r.id=t.role_id WHERE t.request_id=? AND r.department_id IS NOT NULL",id,id);var targets=new ArrayList<AuthorizationBasis.Target>();boolean global=false;for(var t:ts){if(t.get("department_id")==null){global=true;break;}targets.add(new AuthorizationBasis.Target(UUID.fromString((String)t.get("company_id")),UUID.fromString((String)t.get("department_id")),UUID.fromString((String)t.get("system_id"))));}
            fixed.event(c,f,"OA_DELIVERY_RETRIED","SUCCEEDED",Map.of("reason",reason));return new IdempotencyCoordinator.Completion(new SafeResult(202,json.writeValueAsString(queries.currentView(id)),null),new AuthorizationBasis("retryOaDelivery",global?AuthorizationBasis.Policy.GLOBAL_ONLY:AuthorizationBasis.Policy.SCOPED,global?List.of():targets));
        });
    }
}
