package com.adgn.backend.imports;

import com.adgn.backend.core.audit.*;
import com.adgn.backend.core.idempotency.*;
import com.adgn.backend.core.transaction.*;
import com.adgn.backend.identity.repository.IdentityRepository;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.identity.service.CurrentIdentityReader;
import com.adgn.backend.query.management.*;
import com.adgn.backend.query.preview.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import static com.adgn.backend.imports.ImportPlan.*;

/** Precheck and reports only. Never changes formal personnel, grants, sessions or request state. */
public final class ImportPlanService {
    public record ChangeView(int rowNumber,String action,String targetId,String stableCode,Long expectedVersion,String summary,String companyCode,String departmentCode,String positionCode,List<ValueChange> fieldChanges) { }
    private record Prepared(ImportSnapshot.Snapshot snapshot,ImportPlan plan,Map<String,Object> impactDependencies,Map<String,Object> impact) { }
    private final DataSource source;private final JdbcTemplate jdbc;private final ReadBoundary reads;private final CurrentIdentityReader current;
    private final ObjectProvider<IdempotencyCoordinator> coordinators;private final ImportFileService files;private final ImportPlanner planner;private final ImportSnapshot snapshots;
    private final ImpactService impacts;private final PreviewService digests;private final AuditAppender audit;private final JsonMapper json;
    public ImportPlanService(DataSource source,ReadBoundary reads,CurrentIdentityReader current,ObjectProvider<IdempotencyCoordinator> coordinators,ImportFileService files,ImportPlanner planner,ImportSnapshot snapshots,ImpactService impacts,PreviewService digests,AuditAppender audit,JsonMapper json){this.source=source;this.jdbc=new JdbcTemplate(source);this.reads=reads;this.current=current;this.coordinators=coordinators;this.files=files;this.planner=planner;this.snapshots=snapshots;this.impacts=impacts;this.digests=digests;this.audit=audit;this.json=json;}
    public IdempotencyCoordinator.Outcome precheck(Jwt token,String key,JsonNode raw,String requestId)throws Exception{
        reads.read(()->qualify(token,false),a->null);Input input=ImportInputCodec.parse(raw);UUID idempotencyKey;
        try{idempotencyKey=UUID.fromString(key);if(!idempotencyKey.toString().equalsIgnoreCase(key))throw new IllegalArgumentException();}catch(RuntimeException bad){throw IdentityFailure.invalid();}
        var file=files.get(input.fileId(),token);byte[] inputDigest=digests.digest(List.of(input,file.sha256()));
        Batch existing=reads.read(()->qualify(token,false),a->existing(input,inputDigest));
        var coordinator=coordinators.getIfAvailable();if(coordinator==null)throw new IdentityFailure(503,"DEPENDENCY_UNAVAILABLE");
        var request=new IdempotencyRequest(UUID.fromString(token.getSubject()),"precheckImport","POST","/api/v1/admin/import-batches",idempotencyKey,json.writeValueAsBytes(List.of(input,file.sha256())));
        var reservation=coordinator.reserve(request,()->identity(qualify(token,false)),ImportPlanService::authorize);
        if(reservation instanceof IdempotencyCoordinator.Outcome result){if(result.result().status()<300)get(json.readTree(result.result().body()).path("id").stringValue(),token);return result;}
        String candidate=UUID.randomUUID().toString();Prepared prepared;
        if(existing!=null)prepared=null;
        else{
            var rows=files.readSheet(input.fileId(),input.worksheet(),token);
            prepared=reads.read(()->qualify(token,false),account->{
                var snapshot=snapshots.capture(candidate);var plan=planner.plan(input,rows,snapshot.tables(),account.id());var effect=plan.effects();var systems=new HashSet<String>();
                effect.closedSources().forEach(s->systems.add(s.systemId()));
                for(var src:snapshot.tables().get("authorization_source"))if(effect.inactiveEmployees().contains(src.get("employee_id")))systems.add((String)src.get("system_id"));
                for(var grant:snapshot.tables().get("role_grant"))if(effect.endedGrants().contains(grant.get("id")))for(var baseline:snapshot.tables().get("grant_baseline"))if(baseline.get("id").equals(grant.get("baseline_id")))systems.add((String)baseline.get("system_id"));
                if(systems.isEmpty())return new Prepared(snapshot,plan,Map.of("schemaVersion",1),Map.of());
                var change=new ImpactPolicy.Change(systems,Map.of(),Map.of(),Map.of(),Map.of(),effect.closedSources(),effect.endedGrants(),effect.inactiveEmployees(),effect.revokedAppointments(),List.of(),Set.of(),List.of());
                var analysis=impacts.analyze(new ManagementAccess.Authority(account,null,null,null,Set.of()),change);return new Prepared(snapshot,plan,analysis.dependencies(),analysis.projection().impact());
            });
        }
        // Immutable bytes are checked again outside the mutation guard.
        files.get(input.fileId(),token);
        return coordinator.complete((IdempotencyCoordinator.Ticket)reservation,()->identity(qualify(token,false)),ImportPlanService::authorize,c->{
            c.requireActive(source);Batch found=existing(input,inputDigest);if(found!=null)return completion(found);
            if(prepared==null)throw new IdentityFailure(409,"PREVIEW_STALE");snapshots.requireCurrent(c,candidate,prepared.snapshot().dependencies());
            var plan=prepared.plan();int blocking=(int)plan.issues().stream().filter(i->i.severity().equals("BLOCKING")).count(),warnings=plan.issues().size()-blocking;String state=blocking==0?"READY":"BLOCKED";
            String content=HexFormat.of().formatHex(digests.digest(List.of(input,HexFormat.of().formatHex(inputDigest),plan,prepared.snapshot().dependencies(),prepared.impactDependencies(),prepared.impact())));Instant now=c.now();
            jdbc.update("INSERT INTO import_batch(id,file_id,source_system,source_batch_id,import_type,input_snapshot,input_digest,content_digest,state,planned_counts,blocking_issue_count,warning_count,created_by,created_at) VALUES(?,?,?,?,?,?,?,UNHEX(?),?,?,?,?,?,?)",ps->{ps.setString(1,candidate);ps.setString(2,input.fileId());ps.setString(3,input.sourceSystem());ps.setString(4,input.sourceBatchId());ps.setString(5,input.importType());ps.setString(6,json.writeValueAsString(input));ps.setBytes(7,inputDigest);ps.setString(8,content);ps.setString(9,state);ps.setString(10,json.writeValueAsString(plan.counts()));ps.setInt(11,blocking);ps.setInt(12,warnings);ps.setString(13,c.authority().id());ps.setTimestamp(14,Timestamp.from(now),utc());});
            for(var row:plan.changes()){
                var dependencies=new TreeMap<>(row.dependencies());dependencies.putAll(prepared.snapshot().dependencies());dependencies.put("impactDependencies",prepared.impactDependencies());dependencies.put("impact",prepared.impact());
                jdbc.update("INSERT INTO import_plan_row(batch_id,`row_number`,plan_no,action,target_id,stable_code,company_code,department_code,position_code,expected_version,summary,normalized_values,field_changes,dependency_snapshot) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",candidate,row.rowNumber(),row.planNo(),row.action(),row.targetId(),row.stableCode(),row.companyCode(),row.departmentCode(),row.positionCode(),row.expectedVersion(),row.summary(),json.writeValueAsString(row.normalizedValues()),json.writeValueAsString(row.fieldChanges()),json.writeValueAsString(dependencies));
            }
            for(var issue:plan.issues())jdbc.update("INSERT INTO import_issue(id,batch_id,`row_number`,field,severity,code,message) VALUES(?,?,?,?,?,?,?)",c.newId().toString(),candidate,issue.rowNumber(),issue.field(),issue.severity(),issue.code(),issue.message());
            jdbc.update("UPDATE import_batch SET sealed_at=? WHERE id=? AND sealed_at IS NULL",ps->{ps.setTimestamp(1,Timestamp.from(now),utc());ps.setString(2,candidate);});
            audit.append(c,new AuditRecord("IMPORT_PRECHECKED",candidate,null,null,new AuditRecord.Scope(AuditRecord.Visibility.GLOBAL,null,null,null),"SUCCEEDED",requestId,input.reason(),json.writeValueAsString(Map.of("schemaVersion",1,"batchId",candidate,"fileId",input.fileId(),"state",state,"plannedCounts",plan.counts(),"blockingIssueCount",blocking)),List.of(new AuditRecord.Subject("IMPORT_BATCH",candidate))));
            return completion(new Batch(candidate,input,1,content,state,plan.counts(),null,blocking,warnings,null,now));
        });
    }
    private IdempotencyCoordinator.Completion completion(Batch batch){return new IdempotencyCoordinator.Completion(new SafeResult(201,json.writeValueAsString(batch),"/api/v1/admin/import-batches/"+batch.id()),new AuthorizationBasis("precheckImport",AuthorizationBasis.Policy.GLOBAL_ONLY,List.of()));}
    private Batch existing(Input input,byte[] digest){var rows=jdbc.queryForList("SELECT id,input_digest FROM import_batch WHERE source_system=? AND source_batch_id=? AND sealed_at IS NOT NULL",input.sourceSystem(),input.sourceBatchId());if(rows.isEmpty())return null;if(!java.security.MessageDigest.isEqual(digest,(byte[])rows.getFirst().get("input_digest")))throw new IdentityFailure(409,"SOURCE_BATCH_CONFLICT");return load((String)rows.getFirst().get("id"));}
    public Batch get(String id,Jwt token)throws Exception{return reads.read(()->qualify(token,true),a->load(ManagementInput.identifier(id)));}
    public Page<Batch> list(Jwt token,String batch,String state,String page,String size)throws Exception{return reads.read(()->qualify(token,false),a->{var pagination=ManagementInput.pagination(page,size);var args=new ArrayList<Object>();String where="sealed_at IS NOT NULL";if(batch!=null){where+=" AND source_batch_id=?";args.add(ManagementInput.text(batch,1,100));}if(state!=null){if(!Set.of("BLOCKED","READY","SUCCEEDED","FAILED","STALE").contains(state))throw IdentityFailure.invalid();where+=" AND state=?";args.add(state);}long total=jdbc.queryForObject("SELECT COUNT(*) FROM import_batch WHERE "+where,Long.class,args.toArray());args.add(pagination.pageSize());args.add(pagination.offset());var ids=jdbc.queryForList("SELECT id FROM import_batch WHERE "+where+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",String.class,args.toArray());return new Page<>(ids.stream().map(this::load).toList(),pagination.page(),pagination.pageSize(),total);});}
    public Page<Issue> issues(Jwt token,String batch,String severity,String page,String size)throws Exception{return reads.read(()->qualify(token,true),a->{load(ManagementInput.identifier(batch));var p=ManagementInput.pagination(page,size);String where="batch_id=?";var args=new ArrayList<Object>();args.add(batch);if(severity!=null){if(!Set.of("WARNING","BLOCKING").contains(severity))throw IdentityFailure.invalid();where+=" AND severity=?";args.add(severity);}long total=jdbc.queryForObject("SELECT COUNT(*) FROM import_issue WHERE "+where,Long.class,args.toArray());args.add(p.pageSize());args.add(p.offset());var items=jdbc.query("SELECT `row_number`,field,severity,code,message FROM import_issue WHERE "+where+" ORDER BY `row_number`,id LIMIT ? OFFSET ?",(r,n)->new Issue(r.getInt(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5)),args.toArray());return new Page<>(items,p.page(),p.pageSize(),total);});}
    public Page<ChangeView> changes(Jwt token,String batch,String page,String size)throws Exception{return reads.read(()->qualify(token,true),a->{load(ManagementInput.identifier(batch));var p=ManagementInput.pagination(page,size);long total=jdbc.queryForObject("SELECT COUNT(*) FROM import_plan_row WHERE batch_id=?",Long.class,batch);var items=jdbc.query("SELECT * FROM import_plan_row WHERE batch_id=? ORDER BY `row_number`,plan_no LIMIT ? OFFSET ?",(r,n)->{
            JsonNode normalized=json.readTree(r.getString("normalized_values"));var before=normalized.path("before");boolean newObject=normalized.path("newObject").booleanValue()||before.isObject()&&before.size()==0;Number version=(Number)r.getObject("expected_version");return new ChangeView(r.getInt("row_number"),r.getString("action"),newObject?null:r.getString("target_id"),r.getString("stable_code"),version==null?null:version.longValue(),r.getString("summary"),r.getString("company_code"),r.getString("department_code"),r.getString("position_code"),List.of(json.readValue(r.getString("field_changes"),ValueChange[].class)));
        },batch,p.pageSize(),p.offset());return new Page<>(items,p.page(),p.pageSize(),total);});}
    private Batch load(String id){var rows=jdbc.query("SELECT * FROM import_batch WHERE id=? AND sealed_at IS NOT NULL",(r,n)->new Batch(r.getString("id"),json.readValue(r.getString("input_snapshot"),Input.class),r.getLong("version"),HexFormat.of().formatHex(r.getBytes("content_digest")),r.getString("state"),json.readValue(r.getString("planned_counts"),Counts.class),r.getString("actual_counts")==null?null:json.readValue(r.getString("actual_counts"),Counts.class),r.getInt("blocking_issue_count"),r.getInt("warning_count"),r.getString("failure_code"),r.getTimestamp("created_at",utc()).toInstant()),id);if(rows.isEmpty())throw new IdentityFailure(404,"NOT_FOUND");return rows.getFirst();}
    private IdentityRepository.Account qualify(Jwt token,boolean detail){var account=current.admin(token,false).account();if(!account.superAdmin())throw new IdentityFailure(detail?404:403,detail?"NOT_FOUND":"FORBIDDEN");return account;}
    private static CommandBoundary.Identity<IdentityRepository.Account> identity(IdentityRepository.Account account){return new CommandBoundary.Identity<>(account,new AuditRecord.Actor(AuditRecord.ActorKind.ACCOUNT,account.id()));}
    private static void authorize(IdentityRepository.Account account,AuthorizationBasis basis){if(!account.superAdmin()||basis.policy()!=AuthorizationBasis.Policy.GLOBAL_ONLY||!basis.operationId().equals("precheckImport"))throw new IdentityFailure(403,"FORBIDDEN");}
    private static Calendar utc(){return Calendar.getInstance(TimeZone.getTimeZone("UTC"));}
}
