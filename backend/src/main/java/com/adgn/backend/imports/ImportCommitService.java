package com.adgn.backend.imports;

import com.adgn.backend.core.audit.*;
import com.adgn.backend.core.idempotency.*;
import com.adgn.backend.core.transaction.*;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.identity.service.CurrentIdentityReader;
import com.adgn.backend.people.EmploymentWriter;
import com.adgn.backend.lifecycle.*;
import java.time.Instant;
import com.adgn.backend.query.management.ManagementInput;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.util.*;
import java.sql.Timestamp;
import java.util.concurrent.atomic.AtomicReference;
import static com.adgn.backend.imports.ImportPlan.*;

/** A sealed batch commits once; failures are recorded only after its business transaction rolls back. */
public final class ImportCommitService {
    private static final long MAX=9007199254740991L;
    private record Input(long expectedVersion,String contentDigest) { }
    private final ImportFailureRecorder failureRecorder;private final JdbcTemplate jdbc;private final ReadBoundary reads;private final CommandBoundary writes;private final CurrentIdentityReader current;
    private final ObjectProvider<IdempotencyCoordinator> coordinators;private final ImportPlanService reports;private final ImportSnapshot snapshots;
    private final ObjectProvider<EmployeeStateWriter> employeeStates;private final ObjectProvider<EmploymentLifecycleWriter> employmentLifecycle;
    private final ImportFileService files;private final EmploymentWriter people;private final AuditAppender audit;private final JsonMapper json;
    public ImportCommitService(DataSource source,ReadBoundary reads,CommandBoundary writes,CurrentIdentityReader current,ObjectProvider<IdempotencyCoordinator> coordinators,ImportPlanService reports,ImportSnapshot snapshots,ImportFileService files,EmploymentWriter people,AuditAppender audit,JsonMapper json,ObjectProvider<EmployeeStateWriter> employeeStates,ObjectProvider<EmploymentLifecycleWriter> employmentLifecycle,ImportFailureRecorder failureRecorder){this.failureRecorder=failureRecorder;jdbc=new JdbcTemplate(source);this.reads=reads;this.writes=writes;this.current=current;this.coordinators=coordinators;this.reports=reports;this.snapshots=snapshots;this.files=files;this.people=people;this.audit=audit;this.json=json;this.employeeStates=employeeStates;this.employmentLifecycle=employmentLifecycle;}
    public IdempotencyCoordinator.Outcome commit(Jwt token,String batchId,String key,JsonNode raw,String correlation)throws Exception{
        String id=ManagementInput.identifier(batchId);var batch=reads.read(()->account(token),a->reports.load(id));var in=parse(raw);
        var service=coordinators.getIfAvailable();if(service==null)throw unavailable();
        var basis=new AuthorizationBasis("commitImport",AuthorizationBasis.Policy.GLOBAL_ONLY,List.of());
        var request=new IdempotencyRequest(UUID.fromString(token.getSubject()),"commitImport","POST","/api/v1/admin/import-batches/"+id+"/commits",ManagementInput.key(key),json.writeValueAsBytes(in));
        var reservation=service.reserve(request,()->identity(token,batch,in,correlation),ImportCommitService::authorize);
        if(reservation instanceof IdempotencyCoordinator.Outcome result)return result;
        var rejected=new AtomicReference<String>();
        try{
            if(!batch.state().equals("SUCCEEDED"))files.get(batch.input().fileId(),token);
            var outcome=service.complete((IdempotencyCoordinator.Ticket)reservation,()->identity(token,batch,in,correlation),ImportCommitService::authorize,c->{
                try{
                    var live=reports.load(id);if(live.state().equals("SUCCEEDED"))return completion(live,basis);
                    if(live.version()!=in.expectedVersion())throw new IdentityFailure(409,"VERSION_CONFLICT");
                    if(!live.contentDigest().equals(in.contentDigest()))throw new IdentityFailure(409,"STATE_CONFLICT");
                    if(live.state().equals("BLOCKED"))throw IdentityFailure.invalid();
                    if(live.state().equals("STALE"))throw new IdentityFailure(409,"PREVIEW_STALE");
                    if(!Set.of("READY","FAILED").contains(live.state())||live.version()==MAX)throw new IdentityFailure(409,"STATE_CONFLICT");
                    var rows=jdbc.queryForList("SELECT * FROM import_plan_row WHERE batch_id=? ORDER BY `row_number`,plan_no",id);if(rows.isEmpty())throw new IllegalStateException("Sealed plan missing");
                    var deps=json.readTree((String)rows.getFirst().get("dependency_snapshot"));
                    snapshots.requireCurrent(c,id,json.readValue(json.writeValueAsString(Map.of("schemaVersion",1,"sets",deps.path("sets"))),Map.class));
                    long[] counts=new long[8];for(var row:rows)apply(c,row,counts);
                    var actual=new Counts(counts[0],counts[1],counts[2],counts[3],counts[4],counts[5],counts[6],counts[7]);
                    audit.append(c,new AuditRecord("IMPORT_COMMITTED",id,null,null,new AuditRecord.Scope(AuditRecord.Visibility.GLOBAL,null,null,null),"SUCCEEDED",correlation,live.input().reason(),json.writeValueAsString(Map.of("schemaVersion",1,"batchId",id,"contentDigest",live.contentDigest(),"actualCounts",actual)),List.of(new AuditRecord.Subject("IMPORT_BATCH",id))));
                    if(jdbc.update("UPDATE import_batch SET state='SUCCEEDED',version=version+1,row_version=row_version+1,actual_counts=?,committed_at=?,failure_code=NULL WHERE id=? AND version=? AND state IN ('READY','FAILED') AND row_version<9007199254740991",json.writeValueAsString(actual),Timestamp.from(c.now()),id,live.version())!=1)throw new IdentityFailure(409,"STATE_CONFLICT");
                    return completion(reports.load(id),basis);
                }catch(IdentityFailure failure){
                    if(failure.code().equals("PREVIEW_STALE"))rejected.set("STALE");
                    if(failure.status()==409||failure.status()==422)throw new IdempotencyCoordinator.SafeFailure(failure.status()==422?"VALIDATION_ERROR":Set.of("VERSION_CONFLICT","PREVIEW_STALE").contains(failure.code())?failure.code():"STATE_CONFLICT",basis);throw failure;
                }catch(DuplicateKeyException duplicate){rejected.set("STALE");throw new IdempotencyCoordinator.SafeFailure("PREVIEW_STALE",basis);}
            });
            if(rejected.get()!=null)recordFailure(token,batch,in,correlation,rejected.get());return outcome;
        }catch(IdentityFailure failure){throw failure;}
        catch(Exception storage){var recorded=recordFailure(token,batch,in,correlation,"FAILED");if(recorded.state().equals("SUCCEEDED"))return new IdempotencyCoordinator.Outcome(new SafeResult(200,json.writeValueAsString(recorded),null),false);throw unavailable();}
    }
    private void apply(WriteContext<EmploymentWriter.ImportAuthority> c,Map<String,Object> row,long[] counts){
        String action=(String)row.get("action");var n=json.readTree((String)row.get("normalized_values"));var after=n.path("after");long version=row.get("expected_version")==null?0:((Number)row.get("expected_version")).longValue();
        if(action.equals("UNCHANGED"))counts[2]++;
        else if(Set.of("CREATE_COMPANY","CREATE_DEPARTMENT","CREATE_POSITION","RENAME_COMPANY","RENAME_DEPARTMENT","RENAME_POSITION").contains(action)){
            var kind=EmploymentWriter.Kind.valueOf(n.path("entity").stringValue().toUpperCase(Locale.ROOT));
            people.organization(c,kind,uuid(after,"id"),version,kind==EmploymentWriter.Kind.COMPANY?null:uuid(after,kind==EmploymentWriter.Kind.DEPARTMENT?"company_id":"department_id"),after.path("code").stringValue(),after.path("name").stringValue());counts[action.startsWith("CREATE_")?0:1]++;
        }else if(Set.of("CREATE_EMPLOYEE","RENAME_EMPLOYEE","REMAP_EMPLOYEE_KEY").contains(action)){
            people.employee(c,uuid(after,"id"),version,after.path("employee_key").stringValue(),after.path("name").stringValue(),version==0?after.path("status").stringValue():null);counts[action.equals("CREATE_EMPLOYEE")?0:1]++;
        }else if(action.equals("ADD_EMPLOYMENT")){
            people.initialEmployment(c,uuid(after,"id"),uuid(after,"employee_id"),uuid(after,"company_id"),uuid(after,"department_id"),uuid(after,"position_id"),after.path("status").stringValue(),instant(after,"started_at"),instant(after,"ended_at"));counts[0]++;
        }else if(action.equals("END_EMPLOYMENT")){
            var ended=people.endEmployment(c,uuid(after,"id"),version,instant(after,"ended_at"));var result=employmentLifecycle.getObject().ended(c,ended,c.authority().reason(),c.authority().requestId());counts[3]++;counts[6]+=result.invalidatedGrants();counts[7]+=result.invalidatedAppointments();
        }else if(Set.of("DISABLE_EMPLOYEE","RESTORE_EMPLOYEE").contains(action)){
            var result=employeeStates.getObject().apply(c,after.path("id").stringValue(),new LifecycleInput.EmployeeState(version,after.path("status").stringValue(),c.authority().reason()),c.authority().requestId());if(result.outcome().equals("APPLIED")){counts[action.equals("DISABLE_EMPLOYEE")?4:5]++;counts[6]+=result.invalidatedGrantCount();counts[7]+=result.invalidatedAppointmentCount();}
        }else if(action.equals("MOVE_DEPARTMENT")){
            var moves=new ArrayList<EmploymentWriter.MappingMove>();for(var mapping:n.path("mappingWrites"))moves.add(new EmploymentWriter.MappingMove(uuid(mapping,"id"),mapping.path("expectedVersion").longValue(),mapping.path("after").path("source_path").stringValue()));
            people.moveDepartment(c,uuid(after,"id"),version,uuid(after,"company_id"),moves);counts[1]++;return;
        }else throw IdentityFailure.invalid();
        for(var mapping:n.path("mappingWrites")){
            var a=mapping.path("after");long v=mapping.path("expectedVersion").longValue();
            if(mapping.path("entity").stringValue().equals("organization_mapping"))people.organizationMapping(c,uuid(mapping,"id"),v,a.path("source_system").stringValue(),a.path("source_path").stringValue(),a.path("object_kind").stringValue(),uuid(a,"company_id"),uuid(a,"department_id"),uuid(a,"position_id"));
            else people.sourceIdentity(c,uuid(mapping,"id"),v,a.path("source_system").stringValue(),a.path("source_key").stringValue(),uuid(a,"employee_id"));
        }
    }
    private Batch recordFailure(Jwt token,Batch original,Input in,String correlation,String state)throws Exception{
        return writes.execute(()->identity(token,original,in,correlation),c->failureRecorder.record(c,state));
    }
    private String account(Jwt token){var a=current.admin(token,false).account();if(!a.superAdmin())throw new IdentityFailure(404,"NOT_FOUND");return a.id();}
    private CommandBoundary.Identity<EmploymentWriter.ImportAuthority> identity(Jwt token,Batch b,Input in,String request){String account=account(token);return new CommandBoundary.Identity<>(new EmploymentWriter.ImportAuthority(UUID.fromString(account),UUID.fromString(b.id()),in.expectedVersion(),in.contentDigest(),b.input().reason(),request),new AuditRecord.Actor(AuditRecord.ActorKind.ACCOUNT,account));}
    private static void authorize(EmploymentWriter.ImportAuthority a,AuthorizationBasis basis){if(basis.policy()!=AuthorizationBasis.Policy.GLOBAL_ONLY||!basis.operationId().equals("commitImport"))throw new IdentityFailure(403,"FORBIDDEN");}
    private IdempotencyCoordinator.Completion completion(Batch b,AuthorizationBasis basis){return new IdempotencyCoordinator.Completion(new SafeResult(200,json.writeValueAsString(b),null),basis);}
    private static Instant instant(JsonNode n,String field){var value=n.path(field);return value.isMissingNode()||value.isNull()?null:Instant.parse(value.stringValue());}
    private static UUID uuid(JsonNode n,String field){var value=n.path(field);return value.isMissingNode()||value.isNull()?null:UUID.fromString(value.stringValue());}
    private static Input parse(JsonNode n){if(n==null||!n.isObject()||n.size()!=2||!n.has("expectedVersion")||!n.has("contentDigest")||!n.path("expectedVersion").isIntegralNumber()||!n.path("expectedVersion").canConvertToLong()||!n.path("contentDigest").isString())throw IdentityFailure.invalid();long version=n.path("expectedVersion").longValue();String digest=n.path("contentDigest").stringValue();if(version<0||version>MAX||!digest.matches("[a-f0-9]{64}"))throw IdentityFailure.invalid();return new Input(version,digest);}
    private static IdentityFailure unavailable(){return new IdentityFailure(503,"DEPENDENCY_UNAVAILABLE");}
}
