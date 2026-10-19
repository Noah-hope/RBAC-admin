package com.adgn.backend.people;

import com.adgn.backend.core.audit.AuditAppender;
import com.adgn.backend.core.audit.AuditRecord;
import com.adgn.backend.core.transaction.WriteContext;
import com.adgn.backend.identity.security.IdentityFailure;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import tools.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.*;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

/** Import-only primitives; BE-30 owns batch commit and cross-domain lifecycle orchestration. */
public final class EmploymentWriter {
    public enum Kind { COMPANY, DEPARTMENT, POSITION }
    public record ImportAuthority(UUID accountId,UUID batchId,long batchVersion,String contentDigest,String reason,String requestId) { }
    public record Result(UUID id,long version,boolean changed) { }
    public record EndResult(UUID id,long version,boolean changed,UUID employeeId,UUID companyId,UUID departmentId,boolean lastDepartmentJobEnded) { }
    public record MappingMove(UUID id,long expectedVersion,String newSourcePath) { }
    private static final long MAX=9007199254740991L;
    private final DataSource source;
    private final JdbcTemplate jdbc;
    private final AuditAppender audit;
    private final JsonMapper json;
    public EmploymentWriter(DataSource source,AuditAppender audit,JsonMapper json) {
        this.source=source;this.jdbc=new JdbcTemplate(source);this.audit=audit;this.json=json;
    }
    public Result organization(WriteContext<?> c,Kind kind,UUID id,long version,UUID parent,String code,String name){return checked(c,()->{
        if(kind==null)throw invalid();String table=kind.name().toLowerCase(Locale.ROOT);
        return organizationWork(c,kind,table,id,version,parent,code,name);
    });}
    private Result organizationWork(WriteContext<?> c,Kind kind,String table,UUID id,long version,UUID parent,String code,String name){
        String stable=code(code),label=text(name,100);
        var values=data("code",stable,"name",label);
        String parentColumn=kind==Kind.DEPARTMENT?"company_id":"department_id";
        if(kind==Kind.COMPANY){if(parent!=null)throw invalid();}
        else {required(kind==Kind.DEPARTMENT?"company":"department",parent);values.put(parentColumn,requiredId(parent));}
        var old=load(table,id);
        if(old!=null && (!stable.equals(old.get("code")) || (kind!=Kind.COMPANY && !requiredId(parent).equals(old.get(parentColumn)))))throw conflict("INVALID_OWNERSHIP",422);
        return save(c,table,id,version,values,old,true);
    }
    public Result employee(WriteContext<?> c,UUID id,long version,String key,String name,String initialStatus){return checked(c,()->{
        var old=load("employee",id);var fields=data("employee_key",text(key,100),"name",text(name,100));
        if(old==null){if(!Set.of("ACTIVE","INACTIVE").contains(initialStatus==null?"":initialStatus))throw invalid();fields.put("status",initialStatus);}
        else if(initialStatus!=null)throw invalid(); // State transitions belong to BE-27, never silently restore an employee.
        return save(c,"employee",id,version,fields,old,true);
    });}
    public Result sourceIdentity(WriteContext<?> c,UUID id,long version,String origin,String key,UUID employee){return checked(c,()->{
        required("employee",employee);var old=load("employee_source_identity",id);String system=text(origin,100);
        if(old!=null && !system.equals(old.get("source_system")))throw invalid();
        return save(c,"employee_source_identity",id,version,data("source_system",system,"source_key",text(key,100),"employee_id",requiredId(employee)),old,false);
    });}
    public Result organizationMapping(WriteContext<?> c,UUID id,long version,String origin,String path,String kind,UUID company,UUID department,UUID position){return checked(c,()->{
        ownership(company,department,position,kind);var old=load("organization_mapping",id);String system=text(origin,100);
        if(old!=null && !system.equals(old.get("source_system")))throw invalid();
        return save(c,"organization_mapping",id,version,data("source_system",system,"source_path",text(path,300),"object_kind",kind,
            "company_id",requiredId(company),"department_id",optionalId(department),"position_id",optionalId(position)),old,false);
    });}
    public Result startEmployment(WriteContext<?> c,UUID id,UUID employee,UUID company,UUID department,UUID position,Instant started){return checked(c,()->{
        required("employee",employee);ownership(company,department,position,"POSITION");
        return save(c,"employment",id,0,data("employee_id",requiredId(employee),"company_id",requiredId(company),"department_id",requiredId(department),
            "position_id",requiredId(position),"status","ACTIVE","started_at",micros(started)),load("employment",id),true);
    });}
    public EndResult endEmployment(WriteContext<?> c,UUID id,long version,Instant ended){return checked(c,()->{
        var old=required("employment",id);expected(old,version);
        if(!"ACTIVE".equals(old.get("status")))throw conflict("STATE_CONFLICT",409);
        Instant end=micros(ended);
        if(end!=null && old.get("started_at") instanceof java.time.LocalDateTime start && end.isBefore(start.toInstant(java.time.ZoneOffset.UTC)))throw invalid();
        var result=save(c,"employment",id,version,data("status","ENDED","ended_at",end,"recorded_end_at",c.now()),old,false);
        boolean lost=jdbc.queryForObject("SELECT COUNT(*) FROM employment WHERE employee_id=? AND company_id=? AND department_id=? AND status='ACTIVE'",Long.class,
            old.get("employee_id"),old.get("company_id"),old.get("department_id"))==0;
        return new EndResult(id,result.version(),true,uuid(old,"employee_id"),uuid(old,"company_id"),uuid(old,"department_id"),lost);
    });}
    public Result moveDepartment(WriteContext<?> c,UUID id,long version,UUID destination,List<MappingMove> moves){return checked(c,()->{
        var old=required("department",id);expected(old,version);required("company",destination);
        String target=requiredId(destination),key=requiredId(id);
        if(target.equals(old.get("company_id")))return new Result(id,version,false);
        var authority=(ImportAuthority)c.authority();
        for(String pair:List.of("employment:department_id","management_scope:department_id","business_role:department_id","authorization_source:department_id","change_target:department_id","audit_event:source_department_id")){
            String[] fields=pair.split(":");if(count("SELECT COUNT(*) FROM `"+fields[0]+"` WHERE "+fields[1]+"=?",key)>0)throw conflict("IMPORT_BLOCKED",422);
        }
        if(count("SELECT COUNT(*) FROM audit_subject WHERE subject_id=? OR subject_id IN (SELECT id FROM `position` WHERE department_id=?)",key,key)>0)throw conflict("IMPORT_BLOCKED",422);
        if(count("SELECT COUNT(*) FROM import_plan_row WHERE batch_id<>? AND (target_id=? OR JSON_SEARCH(normalized_values,'one',CAST(? AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_bin) IS NOT NULL OR JSON_SEARCH(dependency_snapshot,'one',CAST(? AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_bin) IS NOT NULL OR JSON_SEARCH(field_changes,'one',CAST(? AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_bin) IS NOT NULL)",authority.batchId().toString(),key,key,key,key)>0)throw conflict("IMPORT_BLOCKED",422);
        if(count("SELECT COUNT(*) FROM impact_preview WHERE JSON_SEARCH(dependency_snapshot,'one',CAST(? AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_bin) IS NOT NULL OR JSON_SEARCH(authority_snapshot,'one',CAST(? AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_bin) IS NOT NULL",key,key)>0)throw conflict("IMPORT_BLOCKED",422);
        if(count("SELECT COUNT(*) FROM change_request WHERE JSON_SEARCH(content,'one',CAST(? AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_bin) IS NOT NULL",key)>0)throw conflict("IMPORT_BLOCKED",422);
        var mappings=jdbc.queryForList("SELECT * FROM organization_mapping WHERE department_id=?",key);
        if(moves==null || moves.size()!=mappings.size())throw invalid();
        var indexed=new HashMap<String,MappingMove>();for(var m:moves){if(m==null || indexed.put(requiredId(m.id()),m)!=null)throw invalid();}
        for(var mapping:mappings){var move=indexed.get(mapping.get("id"));if(move==null)throw invalid();expected(mapping,move.expectedVersion());if(move.expectedVersion()==MAX)throw conflict("STATE_CONFLICT",409);text(move.newSourcePath(),300);}
        jdbc.update("DELETE FROM organization_mapping WHERE department_id=?",key);
        var result=save(c,"department",id,version,data("company_id",target),old,true);
        for(var mapping:mappings){var move=indexed.get(mapping.get("id"));
            var fields=data("source_system",mapping.get("source_system"),"source_path",text(move.newSourcePath(),300),"object_kind",mapping.get("object_kind"),"company_id",target,
                    "department_id",key,"position_id",mapping.get("position_id"));
            insert("organization_mapping",(String)mapping.get("id"),((Number)mapping.get("version")).longValue()+1,fields);
            append(c,"organization_mapping",UUID.fromString((String)mapping.get("id")),mapping,required("organization_mapping",UUID.fromString((String)mapping.get("id"))));
        }
        return result;
    });}
    private <T> T checked(WriteContext<?> context,Supplier<T> work){
        Objects.requireNonNull(context).requireActive(source);
        try{authorize(context);return work.get();}catch(RuntimeException failure){context.markFailed(failure);throw failure;}
    }
    private void authorize(WriteContext<?> c){
        if(!(c.authority() instanceof ImportAuthority a) || c.actor().kind()!=AuditRecord.ActorKind.ACCOUNT || !c.actor().id().equals(requiredId(a.accountId())))throw conflict("FORBIDDEN",403);
        if(a.contentDigest()==null || !a.contentDigest().matches("[0-9a-fA-F]{64}"))throw invalid();
        text(a.reason(),500);String request=text(a.requestId(),64);if(!request.matches("[A-Za-z0-9][A-Za-z0-9._:-]*"))throw invalid();
        if(count("SELECT COUNT(*) FROM admin_account a LEFT JOIN employee e ON e.id=a.employee_id WHERE a.id=? AND a.enabled=1 AND a.is_super_admin=1 AND a.must_change_password=0 AND (a.employee_id IS NULL OR e.status='ACTIVE')",a.accountId().toString())!=1)throw conflict("FORBIDDEN",403);
        var batch=required("import_batch",a.batchId());
        if(!Set.of("READY","FAILED").contains((String)batch.get("state")) || batch.get("sealed_at")==null || ((Number)batch.get("version")).longValue()!=a.batchVersion()
                || !a.contentDigest().equalsIgnoreCase(java.util.HexFormat.of().formatHex((byte[])batch.get("content_digest"))))throw conflict("STATE_CONFLICT",409);
    }
    private Result save(WriteContext<?> c,String table,UUID id,long expected,LinkedHashMap<String,Object> fields,Map<String,Object> old,boolean created){
        String key=requiredId(id);
        if(old==null){if(expected!=0)throw conflict("VERSION_CONFLICT",409);var all=new LinkedHashMap<>(fields);if(created)all.put("created_at",c.now());insert(table,key,1,all);append(c,table,id,Map.of(),required(table,id));return new Result(id,1,true);}
        expected(old,expected);
        boolean same=fields.entrySet().stream().allMatch(e->Objects.equals(old.get(e.getKey()),e.getValue()));
        if(same)return new Result(id,expected,false);
        if(expected==MAX)throw conflict("STATE_CONFLICT",409);
        var args=new ArrayList<>(fields.values());args.add(key);args.add(expected);
        String setters=String.join(",",fields.keySet().stream().map(k->"`"+k+"`=?").toList());
        update("UPDATE `"+table+"` SET "+setters+",version=version+1 WHERE id=? AND version=?",args);
        append(c,table,id,old,required(table,id));return new Result(id,expected+1,true);
    }
    private void insert(String table,String id,long version,LinkedHashMap<String,Object> fields){
        var all=new LinkedHashMap<>(fields);all.put("id",id);all.put("version",version);
        update("INSERT INTO `"+table+"` ("+String.join(",",all.keySet().stream().map(k->"`"+k+"`").toList())+") VALUES("+String.join(",",Collections.nCopies(all.size(),"?"))+")",new ArrayList<>(all.values()));
    }
    private void update(String sql,List<Object> values){
        if(jdbc.update(sql,ps->{for(int n=0;n<values.size();n++){Object value=values.get(n);if(value instanceof Instant t)ps.setTimestamp(n+1,Timestamp.from(t),Calendar.getInstance(TimeZone.getTimeZone("UTC")));else ps.setObject(n+1,value);}})!=1)throw conflict("VERSION_CONFLICT",409);
    }
    private void ownership(UUID company,UUID department,UUID position,String kind){
        required("company",company);
        if(!Set.of("COMPANY","DEPARTMENT","POSITION").contains(kind==null?"":kind))throw invalid();
        if(kind.equals("COMPANY")){if(department!=null || position!=null)throw invalid();return;}
        var d=required("department",department);if(!requiredId(company).equals(d.get("company_id")))throw conflict("INVALID_OWNERSHIP",422);
        if(kind.equals("DEPARTMENT")){if(position!=null)throw invalid();return;}
        if(!requiredId(department).equals(required("position",position).get("department_id")))throw conflict("INVALID_OWNERSHIP",422);
    }
    private Map<String,Object> load(String table,UUID id){var rows=jdbc.queryForList("SELECT * FROM `"+table+"` WHERE id=?",requiredId(id));return rows.isEmpty()?null:rows.getFirst();}
    private Map<String,Object> required(String table,UUID id){var row=load(table,id);if(row==null)throw conflict("NOT_FOUND",404);return row;}
    private long count(String sql,Object... args){return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,args));}
    private static void expected(Map<String,Object> old,long expected){if(expected<1 || expected>MAX || ((Number)old.get("version")).longValue()!=expected)throw conflict("VERSION_CONFLICT",409);}
    private void append(WriteContext<?> c,String table,UUID id,Map<String,Object> before,Map<String,Object> after){
        var a=(ImportAuthority)c.authority();
        String subject=switch(table){case "employee"->"EMPLOYEE";case "company"->"COMPANY";case "department"->"DEPARTMENT";case "position"->"POSITION";case "employment"->"EMPLOYMENT";default->"MAPPING";};
        UUID company=after.get("company_id")==null?null:uuid(after,"company_id");
        UUID department=after.get("department_id")==null?null:uuid(after,"department_id");
        if(table.equals("company"))company=id;
        if(table.equals("department"))department=id;
        if(table.equals("position"))company=uuid(required("department",department),"company_id");
        audit.append(c,new AuditRecord("IMPORT_"+table.toUpperCase(Locale.ROOT),id.toString(),null,null,new AuditRecord.Scope(AuditRecord.Visibility.GLOBAL,company,department,null),"SUCCEEDED",a.requestId(),a.reason(),
            json.writeValueAsString(Map.of("schemaVersion",1,"batchId",a.batchId().toString(),"before",before,"after",after)),List.of(new AuditRecord.Subject(subject,id.toString()))));
    }
    private static LinkedHashMap<String,Object> data(Object... pairs){var result=new LinkedHashMap<String,Object>();for(int n=0;n<pairs.length;n+=2)result.put((String)pairs[n],pairs[n+1]);return result;}
    private static String text(String value,int max){if(value==null)throw invalid();value=value.strip();if(value.isEmpty() || value.codePointCount(0,value.length())>max)throw invalid();return value;}
    private static String code(String value){value=text(value,100);if(!value.matches("[a-zA-Z0-9][a-zA-Z0-9_.:-]*"))throw invalid();return value;}
    private static String requiredId(UUID id){if(id==null)throw invalid();return id.toString();}
    private static String optionalId(UUID id){return id==null?null:id.toString();}
    private static UUID uuid(Map<String,Object> row,String name){return UUID.fromString((String)row.get(name));}
    private static Instant micros(Instant value){return value==null?null:value.truncatedTo(ChronoUnit.MICROS);}
    private static IdentityFailure invalid(){return IdentityFailure.invalid();}
    private static IdentityFailure conflict(String code,int status){return new IdentityFailure(status,code);}
}
