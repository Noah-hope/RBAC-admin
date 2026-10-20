package com.adgn.importfixture;

import com.adgn.backend.testsupport.IsolatedMySqlDatabase;
import com.adgn.backend.identity.security.PasswordPolicy;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.*;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ImportFileMySqlCheck {
    private IsolatedMySqlDatabase db;private ConfigurableApplicationContext app;private JdbcTemplate jdbc;
    private Path keys,storage;private String base,superId,global,ordinary,restricted;
    private final String password="Fixture-Import-123456";
    private final byte[] csv="key,name\n007,Person\n".getBytes(StandardCharsets.UTF_8);
    private final byte[] hmac=new byte[32];private final JsonMapper json=JsonMapper.builder().build();private final HttpClient http=HttpClient.newHttpClient();
    private String id(){return UUID.randomUUID().toString();}
    @BeforeAll void start()throws Exception{
        keys=Files.createTempDirectory(Path.of(".tmp"),"be28-keys-");storage=Files.createTempDirectory(Path.of(".tmp"),"be28-files-");new java.security.SecureRandom().nextBytes(hmac);
        for(String family:List.of("admin","business","oa")){var key=new RSAKeyGenerator(2048).keyID(family).generate();Files.writeString(keys.resolve(family+".json"),new JWKSet(family.equals("admin")?key:key.toPublicJWK()).toString(!family.equals("admin")));}
        db=new IsolatedMySqlDatabase();startWeb();String hash=new PasswordPolicy().encode(password);superId=id();
        jdbc.update("INSERT INTO admin_account(id,login_name,password_hash,is_super_admin,must_change_password,created_at) VALUES(?,?,?,1,0,UTC_TIMESTAMP(6))",superId,superId,hash);global=login(superId);
        String limited=id(),employee=id(),company=id(),department=id(),position=id(),scope=id();
        jdbc.update("INSERT INTO employee(id,employee_key,name,created_at) VALUES(?,?,'Fixture',UTC_TIMESTAMP(6))",employee,employee);
        jdbc.update("INSERT INTO company(id,code,name,created_at) VALUES(?,?,'Fixture',UTC_TIMESTAMP(6))",company,company);
        jdbc.update("INSERT INTO department(id,company_id,code,name,created_at) VALUES(?,?,?,'Fixture',UTC_TIMESTAMP(6))",department,company,department);
        jdbc.update("INSERT INTO `position`(id,department_id,code,name,created_at) VALUES(?,?,?,'Fixture',UTC_TIMESTAMP(6))",position,department,position);
        jdbc.update("INSERT INTO employment(id,employee_id,company_id,department_id,position_id,created_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))",id(),employee,company,department,position);
        jdbc.update("INSERT INTO admin_account(id,employee_id,login_name,password_hash,must_change_password,created_at) VALUES(?,?,?,?,0,UTC_TIMESTAMP(6))",limited,employee,limited,hash);
        jdbc.update("INSERT INTO management_scope(id,company_id,department_id,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",scope,company,department);
        jdbc.update("INSERT INTO admin_appointment(id,scope_id,account_id,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",id(),scope,limited);ordinary=login(limited);
        String temp=id();jdbc.update("INSERT INTO admin_account(id,login_name,password_hash,is_super_admin,must_change_password,created_at) VALUES(?,?,?,1,1,UTC_TIMESTAMP(6))",temp,temp,hash);restricted=login(temp);
    }
    private void startWeb(){
        app=db.startWeb("--rbac.identity.enabled=true","--rbac.identity.admin-key-id=admin","--rbac.identity.admin-jwk-set="+keys.resolve("admin.json").toAbsolutePath(),
            "--rbac.identity.business-jwk-set="+keys.resolve("business.json").toAbsolutePath(),"--rbac.identity.oa-jwk-set="+keys.resolve("oa.json").toAbsolutePath(),
            "--rbac.identity.login-ip-limit=1000","--rbac.identity.login-account-limit=1000","--rbac.idempotency.enabled=true","--rbac.idempotency.current-key-id=fixture",
            "--rbac.idempotency.keys-json="+json.writeValueAsString(Map.of("fixture",Base64.getEncoder().encodeToString(hmac))),"--rbac.storage.import-directory="+storage.toAbsolutePath());
        jdbc=new JdbcTemplate(app.getBean(DataSource.class));base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort();
    }
    @AfterAll void close()throws Exception{try{if(app!=null)app.close();}finally{try{if(db!=null)db.close();}finally{for(Path dir:List.of(keys,storage)){if(dir!=null){try(var files=Files.list(dir)){for(Path p:files.toList())Files.deleteIfExists(p);}Files.deleteIfExists(dir);}}}}}
    private String login(String login)throws Exception{
        var r=http.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/admin/sessions")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("loginName",login,"password",password)))).build(),HttpResponse.BodyHandlers.ofString());assertThat(r.statusCode()).isEqualTo(201);return body(r).path("accessToken").stringValue();
    }
    private JsonNode body(HttpResponse<String> response){return json.readTree(response.body());}
    private HttpResponse<String> get(String id,String token)throws Exception{
        var b=HttpRequest.newBuilder(URI.create(base+"/api/v1/admin/import-files/"+id)).timeout(Duration.ofSeconds(30)).GET();if(token!=null)b.header("Authorization","Bearer "+token);return http.send(b.build(),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> upload(byte[] bytes,String name,String mime,String token,String key,String extra)throws Exception{
        String boundary="fixture"+id();var out=new ByteArrayOutputStream();
        out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+name+"\"\r\nContent-Type: "+mime+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));out.write(bytes);out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        if(extra!=null)out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"reason\"\r\n\r\n"+extra+"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        var b=HttpRequest.newBuilder(URI.create(base+"/api/v1/admin/import-files")).timeout(Duration.ofSeconds(40)).header("Content-Type","multipart/form-data; boundary="+boundary);
        if(token!=null)b.header("Authorization","Bearer "+token);if(key!=null)b.header("Idempotency-Key",key);
        return http.send(b.POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> upload(String key)throws Exception{return upload(csv,"people.csv","text/csv",global,key,null);}
    private long count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    @Test void publishesSafeMetadataAndDurableBytesWithAuditAndNoPersonnelMutations()throws Exception{
        long employees=count("employee"),files=count("import_file"),audits=count("audit_event");var response=upload(id());assertThat(response.statusCode()).isEqualTo(201);
        var value=body(response);assertThat(value.propertyNames()).containsExactlyInAnyOrder("id","fileName","sha256","sizeBytes","sheetNames","createdAt");
        assertThat(value.path("sheetNames").get(0).stringValue()).isEqualTo("CSV");assertThat(value.path("sizeBytes").longValue()).isEqualTo(csv.length);
        assertThat(value.path("sha256").stringValue()).isEqualTo(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(csv)));
        String id=value.path("id").stringValue();assertThat(response.headers().firstValue("Location")).contains("/api/v1/admin/import-files/"+id);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");assertThat(response.body()).doesNotContain("storageRef",storage.toString(),password);
        assertThat(count("import_file")).isEqualTo(files+1);assertThat(count("audit_event")).isEqualTo(audits+1);assertThat(count("employee")).isEqualTo(employees);
        assertThat(get(id,global).body()).isEqualTo(response.body());
    }
    @Test void idempotencyIgnoresBoundaryButIncludesFilenameAndFileBytes()throws Exception{
        String key=id();long before=count("import_file");var first=upload(key);assertThat(first.statusCode()).isEqualTo(201);var replay=upload(key);
        assertThat(replay.statusCode()).isEqualTo(201);assertThat(body(replay)).isEqualTo(body(first));assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(count("import_file")).isEqualTo(before+1);
        assertThat(upload(csv,"renamed.csv","text/csv",global,key,null).statusCode()).isEqualTo(409);
        assertThat(upload("key,name\n008,Other\n".getBytes(StandardCharsets.UTF_8),"people.csv","text/csv",global,key,null).statusCode()).isEqualTo(409);
    }
    @Test void enforcesCurrentSuperAdminForUploadDetailAndReplay()throws Exception{
        String key=id();var r=upload(key);assertThat(r.statusCode()).isEqualTo(201);String file=body(r).path("id").stringValue();
        assertThat(upload(csv,"people.csv","text/csv",ordinary,id(),null).statusCode()).isEqualTo(403);
        assertThat(upload(csv,"people.csv","text/csv",restricted,id(),null).statusCode()).isEqualTo(403);
        assertThat(upload(csv,"people.csv","text/csv",null,id(),null).statusCode()).isEqualTo(401);
        assertThat(get(file,ordinary).statusCode()).isEqualTo(404);assertThat(get(id(),ordinary).statusCode()).isEqualTo(404);
        jdbc.update("UPDATE admin_account SET enabled=0 WHERE id=?",superId);
        try{assertThat(upload(key).statusCode()).isEqualTo(401);assertThat(get(file,global).statusCode()).isEqualTo(401);}finally{jdbc.update("UPDATE admin_account SET enabled=1 WHERE id=?",superId);}
    }
    @Test void rejectsInvalidMultipartMimeFormulaAndMissingIdempotencyBeforeMetadata()throws Exception{
        long before=count("import_file");
        assertThat(upload(csv,"people.csv","text/csv",global,null,null).statusCode()).isEqualTo(422);
        assertThat(upload(csv,"people.csv","text/csv",global,id(),"unexpected").statusCode()).isEqualTo(422);
        assertThat(upload(csv,"people.xlsm","application/octet-stream",global,id(),null).statusCode()).isEqualTo(422);
        assertThat(upload(csv,"people.csv","application/json",global,id(),null).statusCode()).isEqualTo(422);
        assertThat(upload(new byte[0],"people.csv","text/csv",global,id(),null).statusCode()).isEqualTo(422);
        assertThat(count("import_file")).isEqualTo(before);
    }
    @Test void acceptsMoreThanContainerDefaultOneMiBButRejectsMoreThanTenMiB()throws Exception{
        String text="key,note\nuser,\""+"x".repeat(1024*1024+50)+"\"\n";var valid=upload(text.getBytes(StandardCharsets.UTF_8),"large.csv","text/csv",global,id(),null);assertThat(valid.statusCode()).isEqualTo(201);
        assertThat(upload(new byte[10*1024*1024+1],"huge.csv","text/csv",global,id(),null).statusCode()).isEqualTo(413);
    }
    @Test void xlsxSheetMetadataIsAvailableWithoutSelectingOrImportingAWorksheet()throws Exception{
        Path p=storage.resolve("fixture.xlsx");ImportFileParserTest.zip(p,ImportFileParserTest.packageParts(ImportFileParserTest.sheet("<row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>name</t></is></c></row>")));
        byte[] bytes=Files.readAllBytes(p);Files.delete(p);var r=upload(bytes,"people.xlsx",ImportFileParserTest.XLSX,global,id(),null);assertThat(r.statusCode()).isEqualTo(201);assertThat(body(r).path("sheetNames").get(0).stringValue()).isEqualTo("人员");
    }
    @Test void missingOrCorruptBlobIsNotReportedAsHealthyMetadataOrSuccessfulReplay()throws Exception{
        String key=id();var r=upload(key);assertThat(r.statusCode()).isEqualTo(201);String file=body(r).path("id").stringValue();String ref=jdbc.queryForObject("SELECT storage_ref FROM import_file WHERE id=?",String.class,file);
        Path blob=storage.resolve(ref);Files.delete(blob);assertThat(get(file,global).statusCode()).isEqualTo(503);assertThat(upload(key).statusCode()).isEqualTo(503);
        Files.write(blob,csv);assertThat(upload(key).statusCode()).isEqualTo(201);
        byte[] changed=csv.clone();changed[changed.length-2]='X';Files.write(blob,changed);assertThat(get(file,global).statusCode()).isEqualTo(500);Files.write(blob,csv);
    }
    @Test void auditFailureRollsBackMetadataAndSafeResultAndSameKeyCanRecover()throws Exception{
        String key=id();long before=count("import_file");
        db.execute("CREATE TRIGGER be28_fail_audit BEFORE INSERT ON audit_event FOR EACH ROW BEGIN IF NEW.event_type='IMPORT_FILE_UPLOADED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture'; END IF; END");
        try{assertThat(upload(key).statusCode()).isEqualTo(500);assertThat(count("import_file")).isEqualTo(before);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM http_idempotency WHERE idempotency_key=? AND state='COMPLETED'",Long.class,key)).isZero();}
        finally{db.execute("DROP TRIGGER be28_fail_audit");}
        jdbc.update("UPDATE http_idempotency SET lease_until=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE idempotency_key=? AND state='IN_PROGRESS'",key);
        assertThat(upload(key).statusCode()).isEqualTo(201);assertThat(count("import_file")).isEqualTo(before+1);
    }
    @Test void applicationRestartUsesTheSamePersistedFileAndMetadata()throws Exception{
        var r=upload(id());assertThat(r.statusCode()).isEqualTo(201);String file=body(r).path("id").stringValue();app.close();startWeb();assertThat(get(file,global).statusCode()).isEqualTo(200);assertThat(get(file,global).body()).isEqualTo(r.body());
    }
}
