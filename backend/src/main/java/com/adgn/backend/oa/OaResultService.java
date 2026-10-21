package com.adgn.backend.oa;

import com.adgn.backend.change.*;
import com.adgn.backend.core.audit.AuditRecord;
import com.adgn.backend.core.transaction.*;
import com.adgn.backend.identity.security.*;
import com.adgn.backend.identity.service.CurrentIdentityReader;
import com.adgn.backend.query.management.ManagementInput;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.util.*;
import java.time.*;
import java.security.MessageDigest;

/** 可靠接收先独立提交；不调用权限写端口，也不依赖进程内通知。 */
public final class OaResultService {
    public record Outcome(boolean duplicate,Map<String,Object> receipt){}
    private final JdbcTemplate jdbc;private final CommandBoundary writes;private final CurrentIdentityReader current;private final FixedRequestStore fixed;private final ClaimService claims;private final JsonMapper json;
    public OaResultService(DataSource ds,CommandBoundary writes,CurrentIdentityReader current,FixedRequestStore fixed,ClaimService claims,JsonMapper json){jdbc=new JdbcTemplate(ds);this.writes=writes;this.current=current;this.fixed=fixed;this.claims=claims;this.json=json;}
    public Outcome receive(Jwt jwt,JsonNode input)throws Exception{
        if(input==null||!input.isObject()||!input.propertyNames().equals(Set.of("eventId","requestId","oaApprovalId","submittedVersion","contentDigest","decision","isFinal","decidedAt","comment")))throw IdentityFailure.invalid();
        String event=DraftInput.id(input,"eventId"),request=DraftInput.id(input,"requestId"),approval=DraftInput.id(input,"oaApprovalId");long version=DraftInput.version(input,"submittedVersion");if(!input.path("contentDigest").isString()||!input.path("decision").isString())throw IdentityFailure.invalid();String digest=input.path("contentDigest").stringValue(),decision=input.path("decision").stringValue();
        if(version<1||digest==null||!digest.matches("[a-f0-9]{64}")||!Set.of("APPROVED","REJECTED").contains(Objects.toString(decision,""))||!input.path("isFinal").isBoolean()||!input.path("isFinal").booleanValue()||!input.path("comment").isString())throw IdentityFailure.invalid();
        String comment=ManagementInput.text(input.path("comment").stringValue(),0,500);Instant decided;try{decided=OffsetDateTime.parse(input.path("decidedAt").stringValue()).toInstant();}catch(Exception bad){throw IdentityFailure.invalid();}
        byte[] canonical;try{canonical=CanonicalPayloadCodec.canonical(json.writeValueAsString(input));}catch(IllegalArgumentException bad){throw IdentityFailure.invalid();}byte[] payloadDigest=CanonicalPayloadCodec.digest(canonical);
        return writes.execute(()->{var service=current.service(jwt,IdentityTokens.Family.OA);return new CommandBoundary.Identity<>(service,new AuditRecord.Actor(AuditRecord.ActorKind.SERVICE,service.id()));},c->{
            String instance=c.authority().id();var link=jdbc.queryForList("SELECT oa_instance_id,oa_approval_id FROM oa_link WHERE request_id=?",request);
            if(link.isEmpty())throw new IdentityFailure(409,"OA_LINK_PENDING",5);
            if(!instance.equals(link.getFirst().get("oa_instance_id"))||!approval.equals(link.getFirst().get("oa_approval_id")))throw conflict();
            var prior=jdbc.query("SELECT id,request_id,payload_digest FROM oa_callback_event WHERE oa_instance_id=? AND event_id=?",(r,n)->new Object[]{r.getString(1),r.getString(2),r.getBytes(3)},instance,event);
            if(!prior.isEmpty()){
                var p=prior.getFirst();if(!request.equals(p[1])||!MessageDigest.isEqual(payloadDigest,(byte[])p[2]))throw conflict();var f=fixed.load(request);return receipt(f,(String)p[0],true);
            }
            var f=fixed.load(request);if(version!=f.submittedVersion()||!digest.equals(f.digest()))throw conflict();
            var finals=jdbc.queryForList("SELECT decision,first_event_id FROM oa_final_result WHERE request_id=?",request);boolean duplicate=!finals.isEmpty();if(duplicate&&!decision.equals(finals.getFirst().get("decision")))throw conflict();
            String receipt=c.newId().toString();jdbc.update("INSERT INTO oa_callback_event(id,oa_instance_id,event_id,request_id,payload_digest,safe_payload,received_at) VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(6))",receipt,instance,event,request,payloadDigest,json.writeValueAsString(input));
            if(duplicate)return receipt(f,receipt,true);
            if(!f.state().equals("PENDING")&&!f.state().equals("INVALIDATED"))throw conflict();
            jdbc.update("INSERT INTO oa_final_result(request_id,first_event_id,decision,decided_at,comment,received_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))",ps->{ps.setString(1,request);ps.setString(2,receipt);ps.setString(3,decision);ps.setTimestamp(4,java.sql.Timestamp.from(decided),Calendar.getInstance(TimeZone.getTimeZone("UTC")));ps.setString(5,comment);});
            boolean approved=decision.equals("APPROVED"),invalid=f.state().equals("INVALIDATED");
            if(jdbc.update("UPDATE change_request SET state=?,approval_result=?,execution_status=?,status_version=status_version+1,row_version=row_version+1 WHERE id=? AND status_version=? AND status_version<9007199254740991 AND row_version<9007199254740991",invalid?"INVALIDATED":approved?"APPROVED":"REJECTED",decision,invalid?"INVALIDATED":approved?"PENDING":"NOT_STARTED",request,f.statusVersion())!=1)throw new IdentityFailure(409,"STATE_CONFLICT");
            if(!approved)claims.release(c,request);fixed.event(c,f,approved?"OA_APPROVED":"OA_REJECTED","SUCCEEDED",Map.of("oaApprovalId",approval,"decision",decision,"comment",comment));return receipt(fixed.load(request),receipt,false);
        });
    }
    private Outcome receipt(FixedRequestStore.Fixed f,String receipt,boolean duplicate){return new Outcome(duplicate,Map.of("receiptId",receipt,"requestId",f.id(),"accepted",true,"duplicate",duplicate,"approvalResult",f.approval(),"executionStatus",f.execution(),"retryable",Set.of("APPROVED","EXECUTION_FAILED").contains(f.state())));}
    private static IdentityFailure conflict(){return new IdentityFailure(409,"CALLBACK_CONFLICT");}
}
