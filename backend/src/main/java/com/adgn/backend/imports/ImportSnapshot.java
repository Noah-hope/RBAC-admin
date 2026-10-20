package com.adgn.backend.imports;

import com.adgn.backend.core.transaction.WriteContext;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.query.preview.PreviewService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;

/** Primary snapshot and conservative set fingerprints, including empty sets and new references. */
public final class ImportSnapshot {
    public record Snapshot(Map<String,List<Map<String,Object>>> tables,Map<String,Object> dependencies) { }
    private final DataSource source;private final JdbcTemplate jdbc;private final PreviewService digests;
    private static final Map<String,String> QUERIES;
    static{
        var q=new TreeMap<String,String>();
        q.put("company","SELECT id,code,name,version FROM company ORDER BY id");
        q.put("department","SELECT id,company_id,code,name,version FROM department ORDER BY id");
        q.put("position","SELECT id,department_id,code,name,version FROM `position` ORDER BY id");
        q.put("employee","SELECT id,employee_key,name,status,version FROM employee ORDER BY id");
        q.put("employee_source_identity","SELECT id,source_system,source_key,employee_id,version FROM employee_source_identity ORDER BY id");
        q.put("organization_mapping","SELECT id,source_system,source_path,object_kind,company_id,department_id,position_id,version FROM organization_mapping ORDER BY id");
        q.put("employment","SELECT id,employee_id,company_id,department_id,position_id,status,started_at,ended_at,version FROM employment ORDER BY id");
        q.put("admin_account","SELECT id,employee_id,enabled,is_super_admin,must_change_password,version,credential_version,session_epoch,row_version FROM admin_account ORDER BY id");
        q.put("admin_appointment","SELECT id,scope_id,account_id,status,appointment_version,row_version FROM admin_appointment ORDER BY id");
        q.put("management_scope","SELECT id,company_id,department_id,scope_version,row_version FROM management_scope ORDER BY id");
        q.put("scope_system","SELECT CONCAT(scope_id,':',system_id) AS id,scope_id,system_id FROM scope_system ORDER BY scope_id,system_id");
        q.put("business_system","SELECT id,enabled,version,catalog_version,row_version FROM business_system ORDER BY id");
        q.put("directory_node","SELECT id,system_id,parent_id,node_type,enabled,visibility_permission_id,version FROM directory_node ORDER BY id");
        q.put("permission","SELECT id,system_id,function_id,permission_type,enabled,menu_eligible,version FROM permission ORDER BY id");
        q.put("role_permission","SELECT CONCAT(role_id,':',permission_id) AS id,role_id,permission_id,system_id FROM role_permission ORDER BY role_id,permission_id");
        q.put("business_role","SELECT id,code,name,company_id,department_id,system_id,state,metadata_version,configuration_version,row_version FROM business_role ORDER BY id");
        q.put("authorization_source","SELECT id,employee_id,company_id,department_id,system_id,source_epoch,source_version,row_version FROM authorization_source ORDER BY id");
        q.put("grant_baseline","SELECT id,source_id,role_id,company_id,system_id,relation_version,row_version FROM grant_baseline ORDER BY id");
        q.put("role_grant","SELECT id,baseline_id,relation_version,source_epoch,status FROM role_grant ORDER BY id");
        q.put("change_request","SELECT id,kind,company_id,state,status_version,scope_id,appointment_id,content FROM change_request ORDER BY id");
        q.put("change_target","SELECT CONCAT(request_id,':',line_no) AS id,request_id,line_no,company_id,department_id,system_id,employee_id,role_id,source_id,baseline_id,grant_id,expected_configuration_version,expected_source_epoch,expected_relation_version FROM change_target ORDER BY request_id,line_no");
        QUERIES=Collections.unmodifiableMap(q);
    }
    public ImportSnapshot(DataSource source,PreviewService digests){this.source=source;this.jdbc=new JdbcTemplate(source);this.digests=digests;}
    public Snapshot capture(String excludingBatch){
        if(!TransactionSynchronizationManager.isActualTransactionActive()||!TransactionSynchronizationManager.hasResource(source))throw new IllegalStateException("Import snapshot requires caller transaction on its datasource");
        var tables=new TreeMap<String,List<Map<String,Object>>>();QUERIES.forEach((name,sql)->tables.put(name,rows(sql)));
        var plans=jdbc.queryForList("SELECT target_id,normalized_values,field_changes,dependency_snapshot FROM import_plan_row WHERE batch_id<>? ORDER BY batch_id,`row_number`,plan_no",excludingBatch);
        var previews=jdbc.queryForList("SELECT dependency_snapshot,authority_snapshot FROM impact_preview ORDER BY id");
        var audit=jdbc.queryForList("SELECT source_department_id FROM audit_event WHERE source_department_id IS NOT NULL ORDER BY id");
        var subjects=jdbc.queryForList("SELECT subject_id FROM audit_subject ORDER BY audit_event_id,subject_type,subject_id");
        var references=new ArrayList<Map<String,Object>>();
        for(var department:tables.get("department")){
            String id=(String)department.get("id");var ids=new HashSet<String>();ids.add(id);for(var position:tables.get("position"))if(id.equals(position.get("department_id")))ids.add((String)position.get("id"));
            boolean referenced=audit.stream().anyMatch(r->id.equals(r.get("source_department_id")))||subjects.stream().anyMatch(r->ids.contains(r.get("subject_id")));
            for(var rows:List.of(plans,previews,tables.get("change_request")))for(var row:rows)for(var value:row.values())if(value!=null)for(String target:ids)if(value.toString().equals(target)||value.toString().contains("\""+target+"\""))referenced=true;
            references.add(Map.of("id",id,"referenced",referenced));
        }
        tables.put("departmentReferences",List.copyOf(references));var hashes=new TreeMap<String,String>();tables.forEach((name,records)->hashes.put(name,HexFormat.of().formatHex(digests.digest(records))));
        return new Snapshot(Collections.unmodifiableMap(tables),Map.of("schemaVersion",1,"sets",Collections.unmodifiableMap(hashes)));
    }
    public void requireCurrent(WriteContext<?> c,String batchId,Map<String,Object> dependencies){
        c.requireActive(source);try{if(!capture(batchId).dependencies().equals(dependencies))throw new IdentityFailure(409,"PREVIEW_STALE");}catch(RuntimeException failure){c.markFailed(failure);throw failure;}
    }
    private List<Map<String,Object>> rows(String sql){var output=new ArrayList<Map<String,Object>>();for(var row:jdbc.queryForList(sql)){var normalized=new TreeMap<String,Object>();row.forEach((key,value)->{if(value instanceof LocalDateTime date)value=date.toInstant(ZoneOffset.UTC).toString();else if(value instanceof java.sql.Timestamp time)value=time.toInstant().toString();normalized.put(key,value);});output.add(Collections.unmodifiableMap(normalized));}return List.copyOf(output);}
}
