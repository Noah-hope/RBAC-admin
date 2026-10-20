package com.adgn.backend.query.preview;

import com.adgn.backend.core.transaction.ReadBoundary;
import com.adgn.backend.core.transaction.WriteContext;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.query.management.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.sql.Timestamp;
import java.security.MessageDigest;
import java.util.*;

/** Fixed safe projections; every page rechecks the creator's current single context. */
public final class PreviewService {
    public record Page(List<JsonNode> items,long page,int pageSize,long total){public Page{items=List.copyOf(items);}}
    public record Projection(Map<String,Object> impact,List<JsonNode> employees,List<JsonNode> targets,List<String> warnings){
        public Projection{impact=Map.copyOf(impact);employees=List.copyOf(employees);targets=List.copyOf(targets);warnings=List.copyOf(warnings);}
    }
    public record View(String id,String contentDigest,Instant expiresAt,Map<String,Object> impact,List<String> warnings) { }
    private record Stored(String creator,String authority,Instant expires) { }
    private final DataSource source;
    private final JdbcTemplate jdbc;
    private final ReadBoundary reads;
    private final ManagementAccess access;
    private final JsonMapper json;
    private final Clock clock;
    public PreviewService(DataSource source,ReadBoundary reads,ManagementAccess access,JsonMapper json,Clock clock){
        this.source=source;this.jdbc=new JdbcTemplate(source);this.reads=reads;this.access=access;this.json=json;this.clock=clock;
    }
    public View create(WriteContext<?> context,ManagementAccess.Authority authority,ManagementAccess.Context selection,
            String operation,Object input,Object dependencies,Projection projection,Map<String,String> bindings){
        context.requireActive(source);
        if(!context.actor().id().equals(authority.account().id()))throw new IllegalArgumentException("Preview actor must match the guarded caller");
        String id=context.newId().toString();Instant now=context.now(),expires=now.plusSeconds(300);
        byte[] inputDigest=digest(input),contentDigest=digest(List.of(operation,HexFormat.of().formatHex(inputDigest),dependencies,projection.impact()));
        var result=json.createObjectNode().set("impact",json.valueToTree(projection.impact()));
        result.set("warnings",json.valueToTree(projection.warnings()));result.set("bindings",json.valueToTree(bindings));
        var extras=json.createArrayNode();
        for(var target:projection.targets()){
            String type=target.path("targetType").stringValue();
            if(type.equals("DIRECTORY_NODE") || type.equals("PERMISSION"))extras.add(target);
        }
        result.set("extraTargets",extras);
        jdbc.update("INSERT INTO impact_preview(id,creator_account_id,appointment_id,authority_snapshot,operation,input_digest,content_digest,dependency_snapshot,result_snapshot,expires_at,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",ps->{
            ps.setString(1,id);ps.setString(2,authority.account().id());ps.setString(3,selection.appointmentId());ps.setString(4,json.writeValueAsString(authority(authority,selection)));
            ps.setString(5,operation);ps.setBytes(6,inputDigest);ps.setBytes(7,contentDigest);ps.setString(8,json.writeValueAsString(dependencies));ps.setString(9,json.writeValueAsString(result));
            ps.setTimestamp(10,Timestamp.from(expires),utc());ps.setTimestamp(11,Timestamp.from(now),utc());
        });
        for(var target:projection.targets()){
            String type=target.path("targetType").stringValue();
            if(!type.equals("DIRECTORY_NODE")&&!type.equals("PERMISSION"))insertItem(context,id,type.equals("CHANGE_REQUEST")?"PENDING_REQUEST":type,targetKey(target),target);
        }
        for(var employee:projection.employees())insertItem(context,id,"EMPLOYEE",employee.path("employeeId").stringValue()+":"+employee.path("companyId").stringValue()+":"+employee.path("systemId").stringValue(),employee);
        return new View(id,HexFormat.of().formatHex(contentDigest),expires,projection.impact(),projection.warnings());
    }
    private void insertItem(WriteContext<?> context,String preview,String kind,String key,JsonNode item){
        jdbc.update("INSERT INTO preview_item(id,preview_id,item_kind,stable_key,safe_item,visibility_basis) VALUES(?,?,?,?,?,'{}')",context.newId().toString(),preview,kind,key,json.writeValueAsString(item));
    }
    private static String targetKey(JsonNode target){
        if(!target.path("targetId").isNull())return target.path("targetId").stringValue();
        var s=target.path("source");return s.path("employeeId").stringValue()+":"+s.path("companyId").stringValue()+":"+s.path("departmentId").stringValue()+":"+s.path("systemId").stringValue();
    }
    public byte[] digest(Object value){
        try{return MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(canonical(json.valueToTree(value))));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private JsonNode canonical(JsonNode value){
        if(value.isObject()){
            var object=json.createObjectNode();for(String key:new TreeSet<>(value.propertyNames()))object.set(key,canonical(value.get(key)));return object;
        }
        if(value.isArray()){var array=json.createArrayNode();for(var item:value)array.add(canonical(item));return array;}
        return value;
    }
    public Map<String,String> bindings(WriteContext<?> context,ManagementAccess.Authority authority,ManagementAccess.Context selection,String id,String operation,Object input){
        context.requireActive(source);check(id,authority,selection);
        String snapshot=jdbc.queryForObject("SELECT result_snapshot,operation,input_digest FROM impact_preview WHERE id=?",(rs,n)->{
            if(!rs.getString(2).equals(operation)||!MessageDigest.isEqual(rs.getBytes(3),digest(input)))throw stale();return rs.getString(1);
        },id);
        var node=json.readTree(snapshot).path("bindings");var result=new TreeMap<String,String>();
        for(String key:node.propertyNames())result.put(key,node.get(key).stringValue());return result;
    }
    public void confirm(WriteContext<?> context,ManagementAccess.Authority authority,ManagementAccess.Context selection,String id,String operation,Object input,Object dependencies){
        context.requireActive(source);check(id,authority,selection);
        var rows=jdbc.query("SELECT operation,input_digest,dependency_snapshot FROM impact_preview WHERE id=?",(rs,n)->
                rs.getString(1).equals(operation)&&MessageDigest.isEqual(rs.getBytes(2),digest(input))&&json.readTree(rs.getString(3)).equals(json.valueToTree(dependencies)),id);
        if(rows.size()!=1||!rows.getFirst())throw stale();
    }
    public Page page(Jwt token,ManagementAccess.Context selection,String previewId,boolean employees,String p,String s)throws Exception{
        String id=ManagementInput.identifier(previewId);var page=ManagementInput.pagination(p,s);
        return reads.read(()->{
            try{return access.read(token,selection);}
            catch(IdentityFailure failure){if(failure.status()==403 || failure.status()==409)throw stale();throw failure;}
        },authority->{
            check(id,authority,selection);
            String from=employees?"SELECT safe_item,stable_key AS sort_key FROM preview_item WHERE preview_id=? AND item_kind='EMPLOYEE'":
                    "SELECT safe_item,CONCAT(JSON_UNQUOTE(JSON_EXTRACT(safe_item,'$.targetType')),':',stable_key) COLLATE utf8mb4_0900_bin AS sort_key FROM preview_item WHERE preview_id=? AND item_kind<>'EMPLOYEE' UNION ALL "
                    +"SELECT jt.item,CONCAT(JSON_UNQUOTE(JSON_EXTRACT(jt.item,'$.targetType')),':',JSON_UNQUOTE(JSON_EXTRACT(jt.item,'$.targetId'))) COLLATE utf8mb4_0900_bin FROM impact_preview ip JOIN JSON_TABLE(ip.result_snapshot,'$.extraTargets[*]' COLUMNS(item JSON PATH '$')) jt WHERE ip.id=?";
            Object[] countArgs=employees?new Object[]{id}:new Object[]{id,id};
            long total=jdbc.queryForObject("SELECT COUNT(*) FROM ("+from+") all_items",Long.class,countArgs);
            var args=new ArrayList<Object>(Arrays.asList(countArgs));args.add(page.pageSize());args.add(page.offset());
            var items=jdbc.query("SELECT safe_item FROM ("+from+") all_items ORDER BY sort_key LIMIT ? OFFSET ?",
                    (rs,n)->json.readTree(rs.getString(1)),args.toArray());
            return new Page(items,page.page(),page.pageSize(),total);
        });
    }
    private void check(String id,ManagementAccess.Authority authority,ManagementAccess.Context selection){
        var rows=jdbc.query("SELECT creator_account_id,authority_snapshot,expires_at FROM impact_preview WHERE id=?",(rs,n)->new Stored(rs.getString(1),rs.getString(2),rs.getTimestamp(3,utc()).toInstant()),id);
        if(rows.isEmpty() || !rows.getFirst().creator().equals(authority.account().id()))throw new IdentityFailure(404,"NOT_FOUND");
        var row=rows.getFirst();
        if(!row.expires().isAfter(clock.instant()) || !json.readTree(row.authority()).equals(json.valueToTree(authority(authority,selection))))throw stale();
    }
    private static Map<String,Object> authority(ManagementAccess.Authority a,ManagementAccess.Context c){
        var result=new TreeMap<String,Object>();
        result.put("accountId",a.account().id());result.put("scopeId",a.scopeId());result.put("companyId",a.companyId());result.put("departmentId",a.departmentId());
        result.put("appointmentId",c.appointmentId());result.put("appointmentVersion",c.appointmentVersion());result.put("scopeVersion",c.scopeVersion());
        result.put("systems",a.systems().stream().sorted().toList());return result;
    }
    private static Calendar utc(){return Calendar.getInstance(TimeZone.getTimeZone("UTC"));}
    public static IdentityFailure stale(){return new IdentityFailure(409,"PREVIEW_STALE");}
}
