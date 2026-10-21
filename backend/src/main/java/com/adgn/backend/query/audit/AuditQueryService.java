package com.adgn.backend.query.audit;

import com.adgn.backend.core.transaction.ReadBoundary;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.query.change.*;
import com.adgn.backend.query.management.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

public final class AuditQueryService {
    public record Page(List<Map<String,Object>> items,long page,int pageSize,long total){public Page{items=List.copyOf(items);}}
    private final ReadBoundary reads;private final ManagementAccess access;private final RequestVisibility requests;private final JdbcTemplate jdbc;private final JsonMapper json;
    private static final Map<String,String> EVENT_TYPES=Map.ofEntries(Map.entry("ROLE_DRAFT_CREATED","DRAFT_SAVED"),Map.entry("CHANGE_DRAFT_CREATED","DRAFT_SAVED"),Map.entry("CHANGE_DRAFT_UPDATED","DRAFT_SAVED"),Map.entry("DRAFT_SAVED","DRAFT_SAVED"),Map.entry("CHANGE_SUBMITTED","SUBMITTED"),Map.entry("OA_LINKED","DELIVERY_LINKED"),Map.entry("OA_DELIVERY_FAILED","DELIVERY_FAILED"),Map.entry("SUBMITTED","SUBMITTED"),Map.entry("DELIVERY_FAILED","DELIVERY_FAILED"),Map.entry("DELIVERY_LINKED","DELIVERY_LINKED"),Map.entry("OA_APPROVED","OA_APPROVED"),Map.entry("OA_REJECTED","OA_REJECTED"),Map.entry("EXECUTION_FAILED","EXECUTION_FAILED"),Map.entry("EXECUTED","EXECUTED"),Map.entry("INVALIDATED","INVALIDATED"));
    private static final Map<String,String> MESSAGES=Map.of("DRAFT_SAVED","已保存草稿，尚未提交","SUBMITTED","方案已提交审批，尚未执行","DELIVERY_FAILED","审批投递暂时失败","DELIVERY_LINKED","审批受理关联已保存","OA_APPROVED","审批已批准，实际执行结果另行记录","OA_REJECTED","审批已驳回","EXECUTION_FAILED","执行失败，服务端将继续重试","EXECUTED","方案已执行成功","INVALIDATED","方案已失效");
    public AuditQueryService(DataSource source,ReadBoundary reads,ManagementAccess access,RequestVisibility requests,JsonMapper json){this.jdbc=new JdbcTemplate(source);this.reads=reads;this.access=access;this.requests=requests;this.json=json;}
    private RequestVisibility.Predicate visible(ManagementAccess.Authority a){
        if(a.global())return new RequestVisibility.Predicate("1=1",List.of());
        var rp=requests.predicate(a,"cr");var args=new ArrayList<Object>(List.of(a.companyId(),a.departmentId(),a.scopeId()));args.addAll(rp.args());args.addAll(rp.args());
        return new RequestVisibility.Predicate("a.visibility='SCOPED' AND a.company_id=? AND a.source_department_id=? AND EXISTS(SELECT 1 FROM scope_system ss WHERE ss.scope_id=? AND ss.system_id=a.system_id) AND (a.change_request_id IS NULL OR NOT EXISTS(SELECT 1 FROM change_request cr WHERE cr.id=a.change_request_id) OR EXISTS(SELECT 1 FROM change_request cr WHERE cr.id=a.change_request_id AND ("+rp.sql()+"))) AND NOT EXISTS(SELECT 1 FROM audit_subject ar JOIN change_request cr ON cr.id=ar.subject_id WHERE ar.audit_event_id=a.id AND ar.subject_type IN ('REQUEST','CHANGE_REQUEST') AND NOT ("+rp.sql()+"))",args);
    }
    public Page list(Jwt token,ManagementAccess.Context context,Map<String,String> q)throws Exception{
        QueryInput.allowed(q,Set.of("page","pageSize","companyId","sourceDepartmentId","systemId","employeeId","roleId","actorId","changeRequestId","oaApprovalId","from","to","eventType","result","requestId"));
        var page=ManagementInput.pagination(q.get("page"),q.get("pageSize"));var ids=new LinkedHashMap<String,String>();for(String f:List.of("companyId","sourceDepartmentId","systemId","employeeId","roleId","actorId","changeRequestId","oaApprovalId"))ids.put(f,QueryInput.id(q,f));
        var start=QueryInput.time(q,"from");var end=QueryInput.time(q,"to");if(start!=null&&end!=null&&!start.isBefore(end))throw IdentityFailure.invalid();
        String event=QueryInput.text(q,"eventType",64),result=QueryInput.text(q,"result",64),correlation=QueryInput.text(q,"requestId",64);
        return reads.read(()->access.read(token,context),a->{
            if(!a.global()){
                if(ids.get("companyId")!=null&&!ids.get("companyId").equals(a.companyId())||ids.get("sourceDepartmentId")!=null&&!ids.get("sourceDepartmentId").equals(a.departmentId())||ids.get("systemId")!=null&&!a.system(ids.get("systemId")))throw forbidden();
                String request=ids.get("changeRequestId");if(request!=null&&requests.exists(request))requests.require(a,request,403);
                for(String field:List.of("employeeId","roleId"))if(ids.get(field)!=null)checkSubject(a,field.equals("employeeId")?"EMPLOYEE":"ROLE",ids.get(field));
            }
            var vis=visible(a);var where=new StringBuilder(" WHERE (").append(vis.sql()).append(")");var args=new ArrayList<>(vis.args());
            Map<String,String> columns=Map.of("companyId","company_id","sourceDepartmentId","source_department_id","systemId","system_id","actorId","actor_id");
            for(var f:columns.entrySet())if(ids.get(f.getKey())!=null){where.append(" AND a.").append(f.getValue()).append("=?");args.add(ids.get(f.getKey()));}
            if(ids.get("changeRequestId")!=null){where.append(" AND (a.change_request_id=? OR EXISTS(SELECT 1 FROM audit_subject s WHERE s.audit_event_id=a.id AND s.subject_type IN ('REQUEST','CHANGE_REQUEST') AND s.subject_id=?))");args.add(ids.get("changeRequestId"));args.add(ids.get("changeRequestId"));}
            for(String f:List.of("employeeId","roleId"))if(ids.get(f)!=null){where.append(" AND EXISTS(SELECT 1 FROM audit_subject s WHERE s.audit_event_id=a.id AND s.subject_type=? AND s.subject_id=?)");args.add(f.equals("employeeId")?"EMPLOYEE":"ROLE");args.add(ids.get(f));}
            if(ids.get("oaApprovalId")!=null){where.append(" AND EXISTS(SELECT 1 FROM oa_link l WHERE l.request_id=a.change_request_id AND l.oa_approval_id=?)");args.add(ids.get("oaApprovalId"));}
            if(event!=null){where.append(" AND a.event_type=?");args.add(event);}if(result!=null){where.append(" AND a.result=?");args.add(result);}if(correlation!=null){where.append(" AND a.correlation_id=?");args.add(correlation);}
            if(start!=null){where.append(" AND a.occurred_at>=?");args.add(Timestamp.from(start));}if(end!=null){where.append(" AND a.occurred_at<?");args.add(Timestamp.from(end));}
            long total=jdbc.queryForObject("SELECT COUNT(*) FROM audit_event a"+where,Long.class,args.toArray());args.add(page.pageSize());args.add(page.offset());
            return new Page(jdbc.query("SELECT a.* FROM audit_event a"+where+" ORDER BY a.occurred_at DESC,a.id DESC LIMIT ? OFFSET ?",(r,n)->summary(r),args.toArray()),page.page(),page.pageSize(),total);
        });
    }
    private void checkSubject(ManagementAccess.Authority a,String type,String id){
        if(jdbc.queryForList("SELECT 1 FROM audit_subject WHERE subject_type=? AND subject_id=? LIMIT 1",type,id).isEmpty())return;
        var vis=visible(a);var args=new ArrayList<>(vis.args());args.add(type);args.add(id);
        if(jdbc.queryForList("SELECT 1 FROM audit_event a WHERE ("+vis.sql()+") AND EXISTS(SELECT 1 FROM audit_subject s WHERE s.audit_event_id=a.id AND s.subject_type=? AND s.subject_id=?) LIMIT 1",args.toArray()).isEmpty())throw forbidden();
    }
    public Map<String,Object> get(Jwt token,ManagementAccess.Context context,String value)throws Exception{
        String id=ManagementInput.identifier(value);return reads.read(()->access.read(token,context),a->{var vis=visible(a);var args=new ArrayList<>(vis.args());args.add(id);
            var rows=jdbc.query("SELECT a.* FROM audit_event a WHERE ("+vis.sql()+") AND a.id=?",(r,n)->detail(r),args.toArray());if(rows.isEmpty())throw new IdentityFailure(404,"NOT_FOUND");return rows.getFirst();});
    }
    public Page events(Jwt token,ManagementAccess.Context context,String value,Map<String,String> q)throws Exception{
        String id=ManagementInput.identifier(value);QueryInput.allowed(q,Set.of("page","pageSize"));var page=ManagementInput.pagination(q.get("page"),q.get("pageSize"));
        return reads.read(()->access.read(token,context),a->{
            requests.require(a,id,404);var vis=visible(a);var args=new ArrayList<>(vis.args());args.add(id);var types=new ArrayList<>(EVENT_TYPES.keySet());Collections.sort(types);args.addAll(types);
            String where=" WHERE ("+vis.sql()+") AND a.change_request_id=? AND a.event_type IN ("+String.join(",",Collections.nCopies(types.size(),"?"))+")";
            long total=jdbc.queryForObject("SELECT COUNT(*) FROM audit_event a"+where,Long.class,args.toArray());args.add(page.pageSize());args.add(page.offset());
            return new Page(jdbc.query("SELECT a.* FROM audit_event a"+where+" ORDER BY a.occurred_at ASC,a.id ASC LIMIT ? OFFSET ?",(r,n)->{String type=EVENT_TYPES.get(r.getString("event_type"));var out=new LinkedHashMap<String,Object>();out.put("id",r.getString("id"));out.put("requestId",id);out.put("occurredAt",ChangeQueryService.instant(r,"occurred_at"));out.put("eventType",type);out.put("actorId",r.getString("actor_id"));out.put("message",MESSAGES.get(type));return out;},args.toArray()),page.page(),page.pageSize(),total);
        });
    }
    private Map<String,Object> summary(ResultSet r)throws SQLException{var out=new LinkedHashMap<String,Object>();out.put("id",r.getString("id"));out.put("occurredAt",ChangeQueryService.instant(r,"occurred_at"));out.put("eventType",r.getString("event_type"));out.put("companyId",r.getString("company_id"));out.put("sourceDepartmentId",r.getString("source_department_id"));out.put("systemId",r.getString("system_id"));out.put("actorId",r.getString("actor_id"));out.put("targetId",r.getString("target_id"));out.put("result",r.getString("result"));out.put("changeRequestId",r.getString("change_request_id"));out.put("requestId",r.getString("correlation_id"));out.put("reason",r.getString("reason"));return out;}
    private Map<String,Object> detail(ResultSet r)throws SQLException{
        JsonNode snapshot;try{snapshot=json.readTree(r.getString("event_snapshot"));}catch(RuntimeException bad){throw ChangeQueryService.integrity();}
        var out=new LinkedHashMap<String,Object>();out.put("event",summary(r));out.put("differences",AuditProjection.differences(snapshot));out.put("originalSubmitterId",r.getString("original_submitter_id"));out.put("oaApprovalId",safeId(snapshot,"oaApprovalId"));
        Long version=null;if(snapshot.hasNonNull("submittedVersion")){try{version=com.adgn.backend.change.DraftInput.version(snapshot,"submittedVersion");}catch(RuntimeException bad){throw ChangeQueryService.integrity();}}out.put("submittedVersion",version);
        String code=snapshot.path("failureCode").isString()?snapshot.path("failureCode").stringValue():null;out.put("failureCode",code!=null&&Set.of("SCOPE_NO_LONGER_COVERS","SOURCE_EPOCH_CHANGED","RELATION_VERSION_CHANGED","ROLE_CONFIGURATION_CHANGED","EMPLOYEE_INACTIVE","NO_APPLICABLE_EMPLOYMENT","PERMISSION_UNAVAILABLE","EXECUTION_STORAGE_FAILURE").contains(code)?code:null);return out;
    }
    private String safeId(JsonNode snapshot,String field){if(!snapshot.hasNonNull(field))return null;try{return com.adgn.backend.change.DraftInput.id(snapshot,field);}catch(RuntimeException bad){throw ChangeQueryService.integrity();}}
    private static IdentityFailure forbidden(){return new IdentityFailure(403,"FORBIDDEN");}
}
