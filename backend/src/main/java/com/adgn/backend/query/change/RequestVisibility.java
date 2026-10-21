package com.adgn.backend.query.change;

import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.query.management.ManagementAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import java.util.*;

/** 仅供调用方主库快照内使用：一个当前任命必须覆盖整份申请，不能先分页后裁剪。 */
public final class RequestVisibility {
    public record Predicate(String sql,List<Object> args){public Predicate{args=List.copyOf(args);}}
    private final JdbcTemplate jdbc;
    public RequestVisibility(DataSource source){jdbc=new JdbcTemplate(source);}
    public Predicate predicate(ManagementAccess.Authority a,String alias){
        if(!Set.of("r","cr").contains(alias))throw new IllegalArgumentException("内部SQL别名无效");
        if(a.global())return new Predicate("1=1",List.of());
        String sql="("+integrity(alias)+") AND "+alias+".company_id=? AND EXISTS(SELECT 1 FROM change_target t WHERE t.request_id="+alias+".id) "
            +"AND NOT EXISTS(SELECT 1 FROM change_target t LEFT JOIN business_role br ON br.id=t.role_id WHERE t.request_id="+alias+".id AND ("
            +"t.company_id<>? OR t.department_id IS NULL OR t.department_id<>? "
            +"OR NOT EXISTS(SELECT 1 FROM scope_system ss WHERE ss.scope_id=? AND ss.system_id=t.system_id) "
            +"OR br.id IS NULL OR br.company_id<>t.company_id OR br.system_id<>t.system_id "
            +"OR (br.department_id IS NOT NULL AND br.department_id<>?) "
            +"OR (t.line_kind='ROLE_CONFIGURATION' AND br.department_id IS NULL)))";
        return new Predicate(sql,List.of(a.companyId(),a.companyId(),a.departmentId(),a.scopeId(),a.departmentId()));
    }
    /** 内容与可检索目标必须一一对应；投影损坏不能把混合申请缩成可见子集。 */
    public static String integrity(String alias){
        if(!Set.of("r","cr").contains(alias))throw new IllegalArgumentException("内部SQL别名无效");
        String content=alias+".content";
        String config=equal("ct.role_id",content,"'$.roleId'")+" AND "+equal("CAST(ct.expected_configuration_version AS CHAR)",content,"'$.expectedConfigurationVersion'");
        var grant=new ArrayList<String>();
        for(var f:Map.of("employee_id","employeeId","role_id","roleId","company_id","sourceCompanyId","department_id","sourceDepartmentId","expected_configuration_version","expectedRoleConfigurationVersion","expected_source_epoch","expectedSourceEpoch","expected_relation_version","expectedRelationVersion").entrySet()){
            String column="ct."+f.getKey();if(f.getKey().startsWith("expected_"))column="CAST("+column+" AS CHAR)";
            grant.add(equal(column,content,"CONCAT('$.items[',ct.line_no,']."+f.getValue()+"')"));
        }
        String revoke=equal("ct.grant_id",content,"CONCAT('$.items[',ct.line_no,'].grantId')")+" AND "+equal("CAST(ct.expected_relation_version AS CHAR)",content,"CONCAT('$.items[',ct.line_no,'].expectedRelationVersion')");
        return "("+equal(alias+".kind",content,"'$.kind'")+" AND "+equal(alias+".company_id",content,"'$.companyId'")
            +" AND (SELECT COUNT(*) FROM change_target ct WHERE ct.request_id="+alias+".id)=(CASE WHEN "+alias+".kind='ROLE_CONFIGURATION' THEN 1 ELSE JSON_LENGTH("+content+",'$.items') END)"
            +" AND NOT EXISTS(SELECT 1 FROM change_target ct WHERE ct.request_id="+alias+".id AND NOT(ct.company_id="+alias+".company_id AND (CASE WHEN "+alias+".kind='ROLE_CONFIGURATION' THEN ("+config+") WHEN "+alias+".kind='ROLE_GRANT' THEN ("+String.join(" AND ",grant)+") ELSE ("+revoke+") END))))";
    }
    private static String equal(String column,String content,String path){return "("+column+" <=> JSON_UNQUOTE(JSON_EXTRACT("+content+","+path+")))";}
    public boolean exists(String id){return !jdbc.queryForList("SELECT 1 FROM change_request WHERE id=?",id).isEmpty();}
    public boolean visible(ManagementAccess.Authority a,String id){var p=predicate(a,"r");var args=new ArrayList<Object>();args.add(id);args.addAll(p.args());return !jdbc.queryForList("SELECT 1 FROM change_request r WHERE r.id=? AND ("+p.sql()+")",args.toArray()).isEmpty();}
    public void require(ManagementAccess.Authority a,String id,int status){if(!visible(a,id))throw new IdentityFailure(status,status==404?"NOT_FOUND":"FORBIDDEN");}
}
