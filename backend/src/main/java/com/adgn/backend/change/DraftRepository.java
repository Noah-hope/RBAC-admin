package com.adgn.backend.change;

import com.adgn.backend.core.transaction.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.time.Instant;
import java.util.*;

public final class DraftRepository {
    public record Row(String id,String kind,String companyId,JsonNode content,String reason,long draftVersion,long rowVersion,String state,Instant createdAt) { }
    public record Target(String company,String department,String system,String employee,String role,String source,String baseline,String grant,Long configurationVersion,Long sourceEpoch,Long relationVersion,JsonNode snapshot,boolean needsChange) { }
    private final DataSource source;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    public DraftRepository(DataSource source,JsonMapper json){this.source=source;this.jdbc=new JdbcTemplate(source);this.json=json;}
    public Row get(String id){
        var rows=jdbc.query("SELECT * FROM change_request WHERE id=?",(r,n)->new Row(r.getString("id"),r.getString("kind"),r.getString("company_id"),json.readTree(r.getString("content")),r.getString("reason"),r.getLong("draft_version"),r.getLong("row_version"),r.getString("state"),r.getTimestamp("created_at",Calendar.getInstance(TimeZone.getTimeZone("UTC"))).toInstant()),id);return rows.isEmpty()?null:rows.getFirst();
    }
    public List<Target> targets(String id){return jdbc.query("SELECT * FROM change_target WHERE request_id=? ORDER BY line_no",(r,n)->new Target(r.getString("company_id"),r.getString("department_id"),r.getString("system_id"),r.getString("employee_id"),r.getString("role_id"),r.getString("source_id"),r.getString("baseline_id"),r.getString("grant_id"),(Long)r.getObject("expected_configuration_version"),(Long)r.getObject("expected_source_epoch"),(Long)r.getObject("expected_relation_version"),json.readTree(r.getString("target_snapshot")),false),id);}
    public Row create(WriteContext<?> c,JsonNode content,String reason,List<Target> targets){
        c.requireActive(source);String id=c.newId().toString();jdbc.update("INSERT INTO change_request(id,kind,company_id,content,reason,created_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))",id,content.path("kind").stringValue(),content.path("companyId").stringValue(),json.writeValueAsString(content),reason);replaceTargets(c,id,content.path("kind").stringValue(),targets);return get(id);
    }
    public Row update(WriteContext<?> c,Row old,JsonNode content,String reason,List<Target> targets){
        c.requireActive(source);int changed=jdbc.update("UPDATE change_request SET content=?,reason=?,draft_version=draft_version+1,row_version=row_version+1 WHERE id=? AND state='DRAFT' AND draft_version=? AND row_version<?",json.writeValueAsString(content),reason,old.id(),old.draftVersion(),9007199254740991L);
        if(changed!=1)throw new PersistenceFailure(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR,null);
        replaceTargets(c,old.id(),old.kind(),targets);return get(old.id());
    }
    public void delete(WriteContext<?> c,String id){c.requireActive(source);jdbc.update("DELETE FROM change_target WHERE request_id=?",id);if(jdbc.update("DELETE FROM change_request WHERE id=? AND state='DRAFT'",id)!=1)throw new PersistenceFailure(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR,null);}
    void replaceTargets(WriteContext<?> c,String id,String kind,List<Target> targets){
        c.requireActive(source);jdbc.update("DELETE FROM change_target WHERE request_id=?",id);int line=0;
        for(var t:targets)jdbc.update("INSERT INTO change_target(request_id,line_no,line_kind,company_id,system_id,department_id,employee_id,role_id,source_id,baseline_id,grant_id,expected_configuration_version,expected_source_epoch,expected_relation_version,target_snapshot) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",id,line++,kind,t.company(),t.system(),t.department(),t.employee(),t.role(),t.source(),t.baseline(),t.grant(),t.configurationVersion(),t.sourceEpoch(),t.relationVersion(),json.writeValueAsString(t.snapshot()));
    }
    public Map<String,Object> view(Row r){
        var v=new LinkedHashMap<String,Object>();v.put("id",r.id());v.put("content",r.content());v.put("reason",r.reason());v.put("draftVersion",r.draftVersion());v.put("submittedVersion",null);v.put("contentDigest",null);v.put("statusVersion",1);v.put("state","DRAFT");v.put("deliveryStatus","NOT_SUBMITTED");v.put("approvalResult","NONE");v.put("executionStatus","NOT_STARTED");v.put("originalAuthority",null);v.put("submittedByAccountId",null);v.put("oaApprovalId",null);v.put("retryable",false);v.put("failureCode",null);v.put("nextRetryAt",null);v.put("createdAt",r.createdAt());v.put("submittedAt",null);return v;
    }
}
