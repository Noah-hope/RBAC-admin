package com.adgn.backend.imports;

import com.adgn.backend.core.audit.*;
import com.adgn.backend.core.idempotency.*;
import com.adgn.backend.core.transaction.*;
import com.adgn.backend.identity.repository.IdentityRepository;
import com.adgn.backend.identity.security.IdentityFailure;
import com.adgn.backend.identity.service.CurrentIdentityReader;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.io.*;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;

/** Upload only stages immutable input; precheck and personnel mutations belong to BE-29/30. */
public final class ImportFileService {
    public record FileView(String id,String fileName,String sha256,long sizeBytes,List<String> sheetNames,Instant createdAt){
        public FileView{sheetNames=List.copyOf(sheetNames);}
    }
    private record FileRecord(FileView view,String storageRef){ }
    @FunctionalInterface public interface Source{InputStream open()throws IOException;}
    private final DataSource source;private final JdbcTemplate jdbc;private final ReadBoundary reads;
    private final CurrentIdentityReader current;private final ObjectProvider<IdempotencyCoordinator> coordinators;
    private final ImportFileParser parser;private final ImportFileStore store;private final AuditAppender audit;private final JsonMapper json;
    public ImportFileService(DataSource source,ReadBoundary reads,CurrentIdentityReader current,ObjectProvider<IdempotencyCoordinator> coordinators,
            ImportFileParser parser,ImportFileStore store,AuditAppender audit,JsonMapper json){
        this.source=source;this.jdbc=new JdbcTemplate(source);this.reads=reads;this.current=current;this.coordinators=coordinators;this.parser=parser;this.store=store;this.audit=audit;this.json=json;
    }
    public IdempotencyCoordinator.Outcome upload(Jwt token,String key,String fileName,String mime,Source bytes,String requestId)throws Exception{
        reads.read(()->qualify(token,false),ignored->null);
        UUID parsedKey=key(key);String name=fileName==null?null:fileName.strip();ImportFileParser.extension(name);
        var coordinator=coordinators.getIfAvailable();if(coordinator==null)throw new IdentityFailure(503,"DEPENDENCY_UNAVAILABLE");
        try(var stage=stage(bytes)){
            var inspection=parser.inspect(stage.path(),name,mime);
            var request=new IdempotencyRequest(UUID.fromString(token.getSubject()),"uploadImportFile","POST","/api/v1/admin/import-files",parsedKey,
                json.writeValueAsBytes(List.of(name,stage.sha256(),stage.sizeBytes(),inspection.sheetNames())));
            var reservation=coordinator.reserve(request,()->identity(qualify(token,false)),ImportFileService::authorize);
            if(reservation instanceof IdempotencyCoordinator.Outcome outcome){
                if(outcome.result().status()<300){String id=json.readTree(outcome.result().body()).path("id").stringValue();get(id,token);}
                return outcome;
            }
            // Publish durable bytes outside the guard. An unknown DB commit must never cause blob deletion.
            var stored=store.publish(stage);
            return coordinator.complete((IdempotencyCoordinator.Ticket)reservation,()->identity(qualify(token,false)),ImportFileService::authorize,context->{
                context.requireActive(source);Instant now=context.now();
                var view=new FileView(stored.id().toString(),name,stored.sha256(),stored.sizeBytes(),inspection.sheetNames(),now);
                jdbc.update("INSERT INTO import_file(id,file_name,sha256,size_bytes,storage_ref,sheet_names,uploaded_by,created_at) VALUES(?,?,UNHEX(?),?,?,?,?,?)",ps->{
                    ps.setString(1,view.id());ps.setString(2,name);ps.setString(3,view.sha256());ps.setLong(4,view.sizeBytes());ps.setString(5,stored.storageRef());
                    ps.setString(6,json.writeValueAsString(view.sheetNames()));ps.setString(7,context.authority().id());ps.setTimestamp(8,Timestamp.from(now),utc());
                });
                audit.append(context,new AuditRecord("IMPORT_FILE_UPLOADED",view.id(),null,null,new AuditRecord.Scope(AuditRecord.Visibility.GLOBAL,null,null,null),"SUCCEEDED",requestId,"上传导入文件",json.writeValueAsString(Map.of("schemaVersion",1,"fileId",view.id(),"sha256",view.sha256(),"sizeBytes",view.sizeBytes())),List.of(new AuditRecord.Subject("IMPORT_FILE",view.id()))));
                return new IdempotencyCoordinator.Completion(new SafeResult(201,json.writeValueAsString(view),"/api/v1/admin/import-files/"+view.id()),basis());
            });
        }
    }
    public FileView get(String id,Jwt token)throws Exception{
        var record=record(id,token);verify(record);return record.view();
    }
    /** Explicit worksheet selection for future precheck, with integrity checks before and after parsing. */
    public List<ImportFileParser.Row> readSheet(String id,String sheet,Jwt token)throws Exception{
        var record=record(id,token);var path=store.verifiedPath(record.storageRef(),record.view().sizeBytes(),record.view().sha256());
        var rows=parser.readSheet(path,record.view().fileName(),sheet);verify(record);return rows;
    }
    private FileRecord record(String id,Jwt token)throws Exception{
        String identifier=id==null?"":id.strip();if(!identifier.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,63}"))throw IdentityFailure.invalid();
        return reads.read(()->qualify(token,true),ignored->{
            var rows=jdbc.query("SELECT id,file_name,LOWER(HEX(sha256)) AS sha256,size_bytes,storage_ref,sheet_names,created_at FROM import_file WHERE id=?",(r,n)->{
                List<String> sheets;
                try{sheets=List.of(json.readValue(r.getString("sheet_names"),String[].class));}catch(RuntimeException bad){throw new PersistenceFailure(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR,null);}
                return new FileRecord(new FileView(r.getString("id"),r.getString("file_name"),r.getString("sha256"),r.getLong("size_bytes"),sheets,r.getTimestamp("created_at",utc()).toInstant()),r.getString("storage_ref"));
            },identifier);
            if(rows.isEmpty())throw new IdentityFailure(404,"NOT_FOUND");return rows.getFirst();
        });
    }
    private void verify(FileRecord record){store.verifiedPath(record.storageRef(),record.view().sizeBytes(),record.view().sha256());}
    private ImportFileStore.Stage stage(Source bytes){try{return store.stage(bytes.open());}catch(IOException bad){throw new PersistenceFailure(PersistenceFailure.Kind.DEPENDENCY_UNAVAILABLE,null);}}
    private IdentityRepository.Account qualify(Jwt token,boolean detail){var account=current.admin(token,false).account();if(!account.superAdmin())throw new IdentityFailure(detail?404:403,detail?"NOT_FOUND":"FORBIDDEN");return account;}
    private static CommandBoundary.Identity<IdentityRepository.Account> identity(IdentityRepository.Account account){return new CommandBoundary.Identity<>(account,new AuditRecord.Actor(AuditRecord.ActorKind.ACCOUNT,account.id()));}
    private static void authorize(IdentityRepository.Account current,AuthorizationBasis basis){if(!current.superAdmin() || basis.policy()!=AuthorizationBasis.Policy.GLOBAL_ONLY || !basis.operationId().equals("uploadImportFile"))throw new IdentityFailure(403,"FORBIDDEN");}
    private static AuthorizationBasis basis(){return new AuthorizationBasis("uploadImportFile",AuthorizationBasis.Policy.GLOBAL_ONLY,List.of());}
    private static UUID key(String value){try{UUID id=UUID.fromString(value);if(!id.toString().equalsIgnoreCase(value))throw new IllegalArgumentException();return id;}catch(RuntimeException bad){throw IdentityFailure.invalid();}}
    private static Calendar utc(){return Calendar.getInstance(TimeZone.getTimeZone("UTC"));}
}
