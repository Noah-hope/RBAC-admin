package com.adgn.backend.grant;

import com.adgn.backend.core.transaction.WriteContext;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import static com.adgn.backend.grant.GrantMutationSupport.*;

/** Entire persisted approved projection. Caller owns canonical validation and success bookkeeping. */
public final class GrantApplier {
    private record Request(String kind,String company,String authority,String scope,String reason) { }
    private record Target(int line,String kind,GrantReader.Key key,String system,String source,String baseline,String grant,Long configuration,Long epoch,long relation) { }
    private record Plan(GrantReader.Baseline baseline,GrantReader.Grant grant) { }
    private final GrantMutationSupport db;
    public GrantApplier(DataSource source,GrantReader reader){db=new GrantMutationSupport(source,reader);}
    public AffectedFacts apply(WriteContext<?> c,String requestId){return db.checked(c,()->{
        id(requestId);Request request=request(requestId);
        List<Target> targets=db.jdbc.query("SELECT * FROM change_target WHERE request_id=? ORDER BY line_no",(r,n)->new Target(r.getInt("line_no"),r.getString("line_kind"),new GrantReader.Key(r.getString("employee_id"),r.getString("role_id"),r.getString("company_id"),r.getString("department_id")),r.getString("system_id"),r.getString("source_id"),r.getString("baseline_id"),r.getString("grant_id"),nullable(r,"expected_configuration_version"),nullable(r,"expected_source_epoch"),r.getLong("expected_relation_version")),requestId);
        require(!targets.isEmpty() && targets.size()<=100,"STATE_CONFLICT");
        var plans=new ArrayList<Plan>();var unique=new HashSet<String>();boolean granting=request.kind().equals("ROLE_GRANT");
        for(int n=0;n<targets.size();n++){
            Target t=targets.get(n);key(t.key());version(t.relation());
            require(t.line()==n && t.kind().equals(request.kind()) && unique.add(t.baseline()),"STATE_CONFLICT");
            var b=db.reader.baseline(t.key());
            require(Objects.equals(b.id(),t.baseline()) && Objects.equals(b.source().id(),t.source()) && b.role().systemId().equals(t.system()) && b.role().companyId().equals(request.company()),"INVALID_OWNERSHIP");
            require(db.jdbc.queryForObject("SELECT COUNT(*) FROM relation_change_claim WHERE baseline_id=? AND request_id=?",Integer.class,b.id(),requestId)==1,"STATE_CONFLICT");
            scope(request,b);
            if(granting){
                require(t.epoch()!=null && t.epoch()==b.source().sourceEpoch(),"SOURCE_EPOCH_CHANGED");
                require(t.configuration()!=null && t.configuration()==b.role().configurationVersion(),"ROLE_CONFIGURATION_CHANGED");
                require(b.relationVersion()==t.relation(),"RELATION_VERSION_CHANGED");
                require(b.activeGrantId()==null,"ALREADY_GRANTED");
                require(b.eligibleForGrant(),"QUALIFICATION_LOST");configuration(b.role());
                plans.add(new Plan(b,null));
            }else{
                var grant=db.active(b);
                require(grant!=null && grant.id().equals(t.grant()) && grant.relationVersion()==t.relation() && b.relationVersion()==t.relation(),"RELATION_VERSION_CHANGED");
                plans.add(new Plan(b,grant));
            }
            db.canAdvance(b);
        }
        // No business DML until the complete immutable target list has been checked.
        var facts=new ArrayList<AffectedFacts.RelationChange>();
        for(Plan plan:plans){var b=plan.baseline();
            if(!granting){facts.add(db.end(c,b,plan.grant(),"REVOKED",requestId,request.reason()));continue;}
            db.advance(b);String grant=c.newId().toString();
            db.jdbc.update("INSERT INTO role_grant(id,baseline_id,relation_version,source_epoch,granted_by_request_id,created_at) VALUES(?,?,?,?,?,?)",ps->{
                ps.setString(1,grant);ps.setString(2,b.id());ps.setLong(3,b.relationVersion()+1);ps.setLong(4,b.source().sourceEpoch());ps.setString(5,requestId);ps.setTimestamp(6,Timestamp.from(c.now()),Calendar.getInstance(TimeZone.getTimeZone("UTC")));
            });
            for(var employment:b.employments())db.jdbc.update("INSERT INTO grant_employment_evidence(grant_id,employment_id) VALUES(?,?)",grant,employment.id().toString());
            facts.add(db.fact(b,grant,"ACTIVE"));
        }
        return new AffectedFacts(List.of(),facts);
    });}
    private Request request(String id){
        var found=db.jdbc.query("""
            SELECT cr.kind,cr.company_id,cr.authority_type,cr.scope_id,cr.reason FROM change_request cr
            JOIN oa_link link ON link.request_id=cr.id
            JOIN oa_final_result final ON final.request_id=cr.id AND final.decision='APPROVED'
            WHERE cr.id=? AND cr.kind IN ('ROLE_GRANT','ROLE_REVOCATION')
              AND cr.state IN ('APPROVED','EXECUTION_FAILED') AND cr.approval_result='APPROVED'
              AND cr.delivery_status='LINKED' AND cr.execution_status IN ('PENDING','FAILED')
              AND NOT EXISTS(SELECT 1 FROM execution_success success WHERE success.request_id=cr.id)
            """,(r,n)->new Request(r.getString("kind"),r.getString("company_id"),r.getString("authority_type"),r.getString("scope_id"),r.getString("reason")),id);
        require(found.size()==1,"STATE_CONFLICT");return found.getFirst();
    }
    private void scope(Request request,GrantReader.Baseline b){
        if(request.authority().equals("GLOBAL"))return;
        require(request.authority().equals("SCOPE") && request.scope()!=null,"ORIGINAL_SCOPE_LOST");
        int covered=db.jdbc.queryForObject("SELECT COUNT(*) FROM management_scope scope JOIN scope_system sys ON sys.scope_id=scope.id AND sys.system_id=? WHERE scope.id=? AND scope.company_id=? AND scope.department_id=?",Integer.class,b.role().systemId(),request.scope(),b.key().sourceCompanyId(),b.key().sourceDepartmentId());
        require(covered==1 && (b.role().departmentId()==null || b.role().departmentId().equals(b.key().sourceDepartmentId())),"ORIGINAL_SCOPE_LOST");
    }
    private void configuration(GrantReader.Role role){
        var references=db.jdbc.queryForList("""
            SELECT rp.system_id AS relation_system,p.id,p.system_id,p.permission_type,p.function_id,n.system_id AS function_system,n.node_type
            FROM role_permission rp LEFT JOIN permission p ON p.id=rp.permission_id
            LEFT JOIN directory_node n ON n.id=p.function_id WHERE rp.role_id=?
            """,role.id());
        require(!references.isEmpty(),"INVALID_ROLE_CONFIGURATION");
        for(var p:references){
            require(p.get("id")!=null && role.systemId().equals(p.get("system_id")) && role.systemId().equals(p.get("relation_system")),"INVALID_ROLE_CONFIGURATION");
            if("SYSTEM_ACCESS".equals(p.get("permission_type")))require(p.get("function_id")==null,"INVALID_ROLE_CONFIGURATION");
            else require("FUNCTION_OPERATION".equals(p.get("permission_type")) && "FUNCTION".equals(p.get("node_type")) && role.systemId().equals(p.get("function_system")),"INVALID_ROLE_CONFIGURATION");
        }
    }
    private static Long nullable(ResultSet r,String field)throws SQLException{Number n=(Number)r.getObject(field);return n==null?null:n.longValue();}
}
