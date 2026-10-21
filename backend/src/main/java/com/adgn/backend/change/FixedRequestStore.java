package com.adgn.backend.change;

import com.adgn.backend.core.audit.*;
import com.adgn.backend.core.transaction.*;
import com.adgn.backend.query.change.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.util.*;
import java.time.Instant;
import java.security.MessageDigest;

/** 读取固定内容并逐项核对七字段包、原字节、摘要及持久目标投影。 */
public final class FixedRequestStore {
    public record Fixed(String id,String state,String delivery,String approval,String execution,long statusVersion,long submittedVersion,String digest,JsonNode payload,JsonNode content,JsonNode original,String submitter,String scopeId,String company,String reason){}
    private final JdbcTemplate jdbc;private final JsonMapper json;private final AuditAppender audit;private final DraftRepository drafts;
    public FixedRequestStore(DataSource ds,JsonMapper json,AuditAppender audit,DraftRepository drafts){jdbc=new JdbcTemplate(ds);this.json=json;this.audit=audit;this.drafts=drafts;}
    public Fixed load(String id){return jdbc.query("SELECT r.*,("+RequestVisibility.integrity("r")+") AS projection_valid FROM change_request r WHERE r.id=?",(r,n)->{
        try{
            if(!r.getBoolean("projection_valid")||r.getString("state").equals("DRAFT"))throw ChangeQueryService.integrity();
            byte[] bytes=r.getBytes("fixed_payload_bytes"),digest=r.getBytes("content_digest");var fixed=json.readTree(bytes);var content=json.readTree(r.getString("content"));var original=json.readTree(r.getString("original_authority"));
            if(!MessageDigest.isEqual(digest,CanonicalPayloadCodec.digest(bytes))||!Arrays.equals(bytes,CanonicalPayloadCodec.canonical(json.writeValueAsString(fixed)))||!fixed.propertyNames().equals(Set.of("requestId","submittedVersion","content","reason","originalAuthority","submittedByAccountId","submittedAt")))throw ChangeQueryService.integrity();
            if(!id.equals(fixed.path("requestId").stringValue())||r.getLong("submitted_version")!=DraftInput.version(fixed,"submittedVersion")||!r.getString("reason").equals(fixed.path("reason").stringValue())||!r.getString("submitted_by_account_id").equals(fixed.path("submittedByAccountId").stringValue())||!equal(content,fixed.path("content"))||!equal(original,fixed.path("originalAuthority"))||!ChangeQueryService.instant(r,"submitted_at").equals(Instant.parse(fixed.path("submittedAt").stringValue())))throw ChangeQueryService.integrity();
            DraftInput.content(content);if(!original.path("kind").stringValue().equals(r.getString("authority_type"))||!original.path("companyId").stringValue().equals(r.getString("company_id"))||!Objects.equals(original.path("scopeId").stringValue(),r.getString("scope_id")))throw ChangeQueryService.integrity();
            return new Fixed(id,r.getString("state"),r.getString("delivery_status"),r.getString("approval_result"),r.getString("execution_status"),r.getLong("status_version"),r.getLong("submitted_version"),HexFormat.of().formatHex(digest),fixed,content,original,r.getString("submitted_by_account_id"),r.getString("scope_id"),r.getString("company_id"),r.getString("reason"));
        }catch(RuntimeException bad){throw ChangeQueryService.integrity();}
    },id).stream().findFirst().orElseThrow(()->new com.adgn.backend.identity.security.IdentityFailure(404,"NOT_FOUND"));}
    private boolean equal(JsonNode a,JsonNode b){return Arrays.equals(CanonicalPayloadCodec.canonical(json.writeValueAsString(a)),CanonicalPayloadCodec.canonical(json.writeValueAsString(b)));}
    public UUID event(WriteContext<?> c,Fixed f,String type,String result,Object details){
        var ts=drafts.targets(f.id());var t=ts.getFirst();boolean scoped=t.department()!=null&&ts.stream().allMatch(x->Objects.equals(x.department(),t.department())&&x.system().equals(t.system()));
        return audit.append(c,new AuditRecord(type,f.id(),UUID.fromString(f.id()),UUID.fromString(f.submitter()),new AuditRecord.Scope(scoped?AuditRecord.Visibility.SCOPED:AuditRecord.Visibility.GLOBAL,UUID.fromString(f.company()),scoped?UUID.fromString(t.department()):null,scoped?UUID.fromString(t.system()):null),result,c.newId().toString(),f.reason(),json.writeValueAsString(Map.of("schemaVersion",1,"after",details)),List.of(new AuditRecord.Subject("CHANGE_REQUEST",f.id()))));
    }
    public static CommandBoundary.Identity<String> worker(){return new CommandBoundary.Identity<>("WORKFLOW",new AuditRecord.Actor(AuditRecord.ActorKind.SERVICE,"workflow-worker"));}
}
