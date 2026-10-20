package com.adgn.backend.query.change;

import com.adgn.backend.change.DraftInput;
import com.adgn.backend.core.transaction.*;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.query.management.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class ChangeQueryService {
    public record Page(List<Map<String,Object>> items,long page,int pageSize,long total){public Page{items=List.copyOf(items);}}
    private final ReadBoundary reads;private final ManagementAccess access;private final RequestVisibility visibility;private final JdbcTemplate jdbc;private final JsonMapper json;
    private static final Set<String> STATES=Set.of("DRAFT","PENDING","REJECTED","APPROVED","EXECUTED","EXECUTION_FAILED","INVALIDATED");
    private static final String SELECT="SELECT r.*,("+RequestVisibility.integrity("r")+") AS projection_valid,l.oa_approval_id FROM change_request r LEFT JOIN oa_link l ON l.request_id=r.id";
    public ChangeQueryService(DataSource source,ReadBoundary reads,ManagementAccess access,RequestVisibility visibility,JsonMapper json){this.jdbc=new JdbcTemplate(source);this.reads=reads;this.access=access;this.visibility=visibility;this.json=json;}
    public Map<String,Object> get(Jwt token,ManagementAccess.Context context,String value)throws Exception{
        String id=ManagementInput.identifier(value);return reads.read(()->access.read(token,context),a->{visibility.require(a,id,404);return jdbc.query(SELECT+" WHERE r.id=?",(r,n)->view(r),id).getFirst();});
    }
    public Page list(Jwt token,ManagementAccess.Context context,Map<String,String> query)throws Exception{
        QueryInput.allowed(query,Set.of("page","pageSize","companyId","systemId","state","deliveryStatus","approvalResult","executionStatus","q"));
        var page=ManagementInput.pagination(query.get("page"),query.get("pageSize"));String company=QueryInput.id(query,"companyId"),system=QueryInput.id(query,"systemId"),q=QueryInput.text(query,"q",100);
        var filters=new LinkedHashMap<String,String>();filters.put("state",QueryInput.enumeration(query,"state",STATES));filters.put("delivery_status",QueryInput.enumeration(query,"deliveryStatus",Set.of("NOT_SUBMITTED","PENDING_SEND","SEND_FAILED","LINKED")));filters.put("approval_result",QueryInput.enumeration(query,"approvalResult",Set.of("NONE","APPROVED","REJECTED")));filters.put("execution_status",QueryInput.enumeration(query,"executionStatus",Set.of("NOT_STARTED","PENDING","SUCCEEDED","FAILED","INVALIDATED")));
        return reads.read(()->access.read(token,context),a->{
            if(!a.global()&&(company!=null&&!company.equals(a.companyId())||system!=null&&!a.system(system)))throw new IdentityFailure(403,"FORBIDDEN");
            var visible=visibility.predicate(a,"r");var where=new StringBuilder(" WHERE (").append(visible.sql()).append(")");var args=new ArrayList<>(visible.args());
            if(company!=null){where.append(" AND r.company_id=?");args.add(company);}if(system!=null){where.append(" AND EXISTS(SELECT 1 FROM change_target t WHERE t.request_id=r.id AND t.system_id=?)");args.add(system);}
            for(var f:filters.entrySet())if(f.getValue()!=null){where.append(" AND r.").append(f.getKey()).append("=?");args.add(f.getValue());}
            if(q!=null){where.append(" AND r.id LIKE ? ESCAPE '='");args.add(ManagementInput.like(q));}
            long total=jdbc.queryForObject("SELECT COUNT(*) FROM change_request r"+where,Long.class,args.toArray());args.add(page.pageSize());args.add(page.offset());
            return new Page(jdbc.query(SELECT+where+" ORDER BY r.created_at DESC,r.id DESC LIMIT ? OFFSET ?",(r,n)->view(r),args.toArray()),page.page(),page.pageSize(),total);
        });
    }
    private Map<String,Object> view(ResultSet r)throws SQLException{
        try{
            if(!r.getBoolean("projection_valid"))throw integrity();
            JsonNode content=json.readTree(r.getString("content"));DraftInput.content(content);
            String kind=r.getString("kind"),company=r.getString("company_id"),state=r.getString("state"),delivery=r.getString("delivery_status");
            if(!content.path("kind").stringValue().equals(kind)||!content.path("companyId").stringValue().equals(company)||!STATES.contains(state))throw integrity();
            var out=new LinkedHashMap<String,Object>();out.put("id",r.getString("id"));out.put("content",content);out.put("reason",r.getString("reason"));out.put("draftVersion",ManagementInput.version(r.getLong("draft_version")));out.put("statusVersion",ManagementInput.version(r.getLong("status_version")));
            boolean draft=state.equals("DRAFT");Long submitted=(Long)r.getObject("submitted_version");byte[] digest=r.getBytes("content_digest");
            if(!draft&&(submitted==null||submitted<1||submitted>ManagementInput.MAX_VERSION||digest==null||digest.length!=32))throw integrity();
            out.put("submittedVersion",submitted);out.put("contentDigest",digest==null?null:HexFormat.of().formatHex(digest));out.put("state",state);out.put("deliveryStatus",delivery);out.put("approvalResult",r.getString("approval_result"));out.put("executionStatus",r.getString("execution_status"));
            out.put("originalAuthority",draft?null:authority(r,company));out.put("submittedByAccountId",r.getString("submitted_by_account_id"));
            String approval=r.getString("oa_approval_id");if(delivery.equals("LINKED")&&approval==null)throw integrity();out.put("oaApprovalId",delivery.equals("LINKED")?approval:null);
            out.put("retryable",Set.of("APPROVED","EXECUTION_FAILED").contains(state));out.put("failureCode",safeFailure(r.getString("failure_code"),state));out.put("nextRetryAt",instant(r,"next_retry_at"));out.put("createdAt",instant(r,"created_at"));out.put("submittedAt",instant(r,"submitted_at"));return out;
        }catch(RuntimeException bad){throw integrity();}
    }
    private JsonNode authority(ResultSet r,String company)throws SQLException{
        var node=json.readTree(r.getString("original_authority"));var names=Set.of("kind","scopeId","scopeVersion","appointmentId","appointmentVersion","companyId","departmentId","systemIds");
        if(node==null||!node.isObject()||node.size()!=names.size())throw integrity();for(String name:names)if(!node.has(name))throw integrity();
        if(!DraftInput.id(node,"companyId").equals(company)||!node.path("kind").stringValue().equals(r.getString("authority_type")))throw integrity();DraftInput.ids(node.path("systemIds"),0,Integer.MAX_VALUE);
        if(node.path("kind").stringValue().equals("GLOBAL")){for(String name:List.of("scopeId","scopeVersion","appointmentId","appointmentVersion","departmentId"))if(!node.path(name).isNull())throw integrity();}
        else if(node.path("kind").stringValue().equals("SCOPE")){
            if(!DraftInput.id(node,"scopeId").equals(r.getString("scope_id"))||!DraftInput.id(node,"appointmentId").equals(r.getString("appointment_id")))throw integrity();DraftInput.id(node,"departmentId");DraftInput.version(node,"scopeVersion");DraftInput.version(node,"appointmentVersion");
        }else throw integrity();return node;
    }
    public static String safeFailure(String code,String state){
        if(code==null){if(Set.of("INVALIDATED","EXECUTION_FAILED").contains(state))throw integrity();return null;}
        var invalid=Set.of("SCOPE_NO_LONGER_COVERS","SOURCE_EPOCH_CHANGED","RELATION_VERSION_CHANGED","ROLE_CONFIGURATION_CHANGED","EMPLOYEE_INACTIVE","NO_APPLICABLE_EMPLOYMENT","PERMISSION_UNAVAILABLE");
        if(state.equals("EXECUTION_FAILED")&&code.equals("EXECUTION_STORAGE_FAILURE")||state.equals("INVALIDATED")&&invalid.contains(code))return code;throw integrity();
    }
    public static Instant instant(ResultSet r,String field)throws SQLException{var t=r.getTimestamp(field,Calendar.getInstance(TimeZone.getTimeZone("UTC")));return t==null?null:t.toInstant();}
    public static PersistenceFailure integrity(){return new PersistenceFailure(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR,null);}
}
