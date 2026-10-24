package com.adgn.scopefixture;

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
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScopeCreationMySqlCheck {
    IsolatedMySqlDatabase db;
    ConfigurableApplicationContext app;
    JdbcTemplate jdbc;
    Path keys;
    String base,token,otherToken,restrictedToken,superAccount,company,department,otherDepartment,system,account,employee,passwordHash;
    final JsonMapper json=JsonMapper.builder().build();
    final HttpClient http=com.adgn.backend.testsupport.ContractHttpClient.create();
    final String password="Fixture-Scope-123456";
    static String id(){return UUID.randomUUID().toString();}
    @BeforeAll void start()throws Exception{
        keys=Files.createTempDirectory(Path.of(".tmp"),"be09-keys-");
        for(String family:List.of("admin","business","oa")){
            var key=new RSAKeyGenerator(2048).keyID(family).generate();
            Files.writeString(keys.resolve(family+".json"),new JWKSet(family.equals("admin")?key:key.toPublicJWK()).toString(!family.equals("admin")));
        }
        byte[] secret=new byte[32];new java.security.SecureRandom().nextBytes(secret);
        db=new IsolatedMySqlDatabase();
        app=db.startWeb("--rbac.identity.enabled=true","--rbac.identity.admin-key-id=admin",
                "--rbac.identity.admin-jwk-set="+keys.resolve("admin.json").toAbsolutePath(),
                "--rbac.identity.business-jwk-set="+keys.resolve("business.json").toAbsolutePath(),
                "--rbac.identity.oa-jwk-set="+keys.resolve("oa.json").toAbsolutePath(),
                "--rbac.identity.login-ip-limit=1000","--rbac.identity.login-account-limit=1000",
                "--rbac.idempotency.enabled=true","--rbac.idempotency.current-key-id=fixture",
                "--rbac.idempotency.keys-json="+json.writeValueAsString(Map.of("fixture",Base64.getEncoder().encodeToString(secret))));
        jdbc=new JdbcTemplate(app.getBean(DataSource.class));
        base="http://127.0.0.1:"+((WebServerApplicationContext)app).getWebServer().getPort()+"/api/v1/admin/";
        passwordHash=new PasswordPolicy().encode(password);
        superAccount=account(null,true,false);token=login(superAccount);
        otherToken=login(account(null,true,false));restrictedToken=login(account(null,true,true));
    }
    @BeforeEach void fixture(){
        company=id();jdbc.update("INSERT INTO company(id,code,name,created_at) VALUES(?,?,'Company',UTC_TIMESTAMP(6))",company,company);
        department=department(company);otherDepartment=department(company);
        system=id();jdbc.update("INSERT INTO business_system(id,code,name,created_at) VALUES(?,?,'System',UTC_TIMESTAMP(6))",system,system);
        employee=employee();account=account(employee,false,false);employ(employee,department);
    }
    String department(String c){String d=id();jdbc.update("INSERT INTO department(id,company_id,code,name,created_at) VALUES(?,?,?,'Dept',UTC_TIMESTAMP(6))",d,c,d);return d;}
    String employee(){String e=id();jdbc.update("INSERT INTO employee(id,employee_key,name,created_at) VALUES(?,?,'Employee',UTC_TIMESTAMP(6))",e,e);return e;}
    void employ(String e,String d){String p=id();jdbc.update("INSERT INTO `position`(id,department_id,code,name,created_at) VALUES(?,?,?,'Position',UTC_TIMESTAMP(6))",p,d,p);
        jdbc.update("INSERT INTO employment(id,employee_id,company_id,department_id,position_id,status,created_at) VALUES(?,?,?,?,?,'ACTIVE',UTC_TIMESTAMP(6))",id(),e,company,d,p);}
    String account(String e,boolean global,boolean restricted){String a=id();jdbc.update("INSERT INTO admin_account(id,employee_id,login_name,password_hash,is_super_admin,must_change_password,created_at) VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(6))",a,e,a,passwordHash,global,restricted);return a;}
    HttpResponse<String> request(String method,String path,Object input,String bearer,String key)throws Exception{
        var b=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30));
        if(bearer!=null)b.header("Authorization","Bearer "+bearer);if(key!=null)b.header("Idempotency-Key",key);
        if(input!=null)b.header("Content-Type","application/json");
        return http.send(b.method(method,input==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input))).build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode body(HttpResponse<String> r){return json.readTree(r.body());}
    String login(String a)throws Exception{var r=request("POST","sessions",Map.of("loginName",a,"password",password),null,null);assertThat(r.statusCode()).isEqualTo(201);return body(r).path("accessToken").stringValue();}
    Map<String,Object> input(List<String> systems,List<String> accounts){return new TreeMap<>(Map.of("companyId",company,"departmentId",department,"systemIds",systems,"accountIds",accounts,"reason","Fixture scope appointment"));}
    Map<String,Object> input(){return input(List.of(system),List.of(account));}
    HttpResponse<String> preview(Object input)throws Exception{return request("POST","management-scope-previews",input,token,null);}
    String previewId(Object input)throws Exception{var r=preview(input);assertThat(r.statusCode()).as("preview: %s",r.body()).isEqualTo(200);return body(r).path("id").stringValue();}
    HttpResponse<String> save(Object input,String preview,String key)throws Exception{return request("POST","management-scopes",Map.of("input",input,"previewId",preview),token,key);}
    long count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    void failure(HttpResponse<String> r,int status,String code){assertThat(r.statusCode()).as("response: %s",r.body()).isEqualTo(status);assertThat(body(r).path("code").stringValue()).isEqualTo(code);}
    @AfterAll void close()throws Exception{try{if(app!=null)app.close();}finally{try{if(db!=null)db.close();}finally{if(keys!=null){for(String f:List.of("admin","business","oa"))Files.deleteIfExists(keys.resolve(f+".json"));Files.deleteIfExists(keys);}}}}

    @Test void previewThenCreateIsAtomicImmediatelyUsableAndDoesNotGrantBusinessPermissions()throws Exception{
        String e2=employee(),a2=account(e2,false,false);employ(e2,department);
        var input=input(List.of(system),List.of(account,a2));long scopes=count("management_scope"),appointments=count("admin_appointment"),audit=count("audit_event");
        var p=preview(input);assertThat(p.statusCode()).as(p.body()).isEqualTo(200);
        assertThat(body(p).path("impact").path("visibleAppointmentCount").intValue()).isEqualTo(2);
        assertThat(body(p).path("impact").path("visibleEmployeeCount").intValue()).isZero();
        assertThat(count("management_scope")).isEqualTo(scopes);assertThat(count("admin_appointment")).isEqualTo(appointments);assertThat(count("audit_event")).isEqualTo(audit);
        String preview=body(p).path("id").stringValue();
        var targets=request("GET","previews/"+preview+"/targets",null,token,null);
        assertThat(targets.statusCode()).as(targets.body()).isEqualTo(200);assertThat(body(targets).path("total").longValue()).isEqualTo(2);
        for(var target:body(targets).path("items"))assertThat(target.path("effect").stringValue()).isEqualTo("CONFIGURATION_CHANGE");
        var result=save(input,preview,id());assertThat(result.statusCode()).as(result.body()).isEqualTo(201);
        assertThat(result.headers().firstValue("Cache-Control")).contains("no-store");
        String scope=body(result).path("scope").path("id").stringValue();
        assertThat(result.headers().firstValue("Location")).contains("/api/v1/admin/management-scopes/"+scope);
        assertThat(body(result).propertyNames()).containsExactlyInAnyOrder("scope","outcome","appointmentsChangedCount","auditEventIds");
        assertThat(body(result).path("appointmentsChangedCount").intValue()).isEqualTo(2);
        assertThat(body(result).path("scope").path("version").longValue()).isEqualTo(1);
        assertThat(count("management_scope")).isEqualTo(scopes+1);assertThat(count("admin_appointment")).isEqualTo(appointments+2);
        assertThat(body(result).path("auditEventIds").size()).isGreaterThan(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_appointment WHERE scope_id=? AND status='ACTIVE' AND appointment_version=1 AND row_version=0",Long.class,scope)).isEqualTo(2);
        assertThat(request("GET","management-scopes/"+scope,null,token,null).statusCode()).isEqualTo(200);
        String candidate=login(account);var mine=request("GET","my-appointments",null,candidate,null);assertThat(mine.statusCode()).isEqualTo(200);assertThat(body(mine).path("total").longValue()).isEqualTo(1);
        assertThat(count("role_grant")).isZero();assertThat(count("authorization_source")).isZero();assertThat(count("change_request")).isZero();
        failure(request("POST","management-scope-previews",input,candidate,null),403,"FORBIDDEN");
    }
    @Test void emptySetsAndMultipleIndependentScopesForOneDepartmentAreLegal()throws Exception{
        var empty=input(List.of(),List.of());var r=save(empty,previewId(empty),id());assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
        assertThat(body(r).path("appointmentsChangedCount").intValue()).isZero();assertThat(body(r).path("scope").path("systemIds").isEmpty()).isTrue();
        var withAdmin=input(List.of(),List.of(account));var a=save(withAdmin,previewId(withAdmin),id());assertThat(a.statusCode()).as(a.body()).isEqualTo(201);
        var b=save(input(),previewId(input()),id());assertThat(b.statusCode()).as(b.body()).isEqualTo(201);
        assertThat(body(a).path("scope").path("id")).isNotEqualTo(body(b).path("scope").path("id"));
        assertThat(body(request("GET","my-appointments",null,login(account),null)).path("total").longValue()).isEqualTo(2);
    }
    @Test void identityAndInputFailuresDoNotCreateBusinessRows()throws Exception{
        long scopes=count("management_scope");failure(request("POST","management-scope-previews",input(),null,null),401,"AUTHENTICATION_FAILED");
        failure(request("POST","management-scope-previews",input(),restrictedToken,null),403,"FORBIDDEN");
        for(String missing:List.of("companyId","departmentId","systemIds","accountIds","reason")){
            var bad=input();bad.remove(missing);failure(preview(bad),422,"VALIDATION_ERROR");
        }
        failure(preview(input(List.of(system,system),List.of(account))),422,"VALIDATION_ERROR");
        failure(preview(input(List.of(system),List.of(account,account))),422,"VALIDATION_ERROR");
        failure(preview(input(List.of("not-found"),List.of(account))),422,"VALIDATION_ERROR");
        assertThat(count("management_scope")).isEqualTo(scopes);
    }
    @Test void qualificationRejectsWrongDepartmentDisabledSystemsAccountsAndEmployees()throws Exception{
        long scopes=count("management_scope");
        jdbc.update("UPDATE business_system SET enabled=0 WHERE id=?",system);failure(preview(input()),422,"VALIDATION_ERROR");jdbc.update("UPDATE business_system SET enabled=1 WHERE id=?",system);
        jdbc.update("UPDATE admin_account SET enabled=0 WHERE id=?",account);failure(preview(input()),422,"VALIDATION_ERROR");jdbc.update("UPDATE admin_account SET enabled=1 WHERE id=?",account);
        jdbc.update("UPDATE employee SET status='INACTIVE' WHERE id=?",employee);failure(preview(input()),422,"VALIDATION_ERROR");jdbc.update("UPDATE employee SET status='ACTIVE' WHERE id=?",employee);
        jdbc.update("UPDATE employment SET status='ENDED',recorded_end_at=UTC_TIMESTAMP(6) WHERE employee_id=?",employee);employ(employee,otherDepartment);failure(preview(input()),422,"VALIDATION_ERROR");
        failure(preview(input(List.of(system),List.of(superAccount))),422,"VALIDATION_ERROR");
        failure(preview(input(List.of(system),List.of(id()))),422,"VALIDATION_ERROR");
        String otherCompany=id();jdbc.update("INSERT INTO company(id,code,name,created_at) VALUES(?,?,'Other company',UTC_TIMESTAMP(6))",otherCompany,otherCompany);
        var mismatch=input();mismatch.put("departmentId",department(otherCompany));failure(preview(mismatch),422,"VALIDATION_ERROR");
        assertThat(count("management_scope")).isEqualTo(scopes);
    }
    @Test void previewChangesAreStaleAndForeignCreatorCannotConfirm()throws Exception{
        var input=input();String p=previewId(input);var changed=new TreeMap<>(input);changed.put("reason","Changed reason");failure(save(changed,p,id()),409,"PREVIEW_STALE");
        failure(request("POST","management-scopes",Map.of("input",input,"previewId",p),otherToken,id()),404,"NOT_FOUND");
        expire(p);failure(save(input,p,id()),409,"PREVIEW_STALE");
    }
    @Test void relevantQualificationChangeInvalidatesPreviewButUnrelatedPersonDoesNot()throws Exception{
        var input=input();String p=previewId(input);long scopes=count("management_scope");
        jdbc.update("UPDATE employment SET version=version+1 WHERE employee_id=?",employee);failure(save(input,p,id()),409,"PREVIEW_STALE");assertThat(count("management_scope")).isEqualTo(scopes);
        p=previewId(input);employee();var r=save(input,p,id());assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
    }
    @Test void lostQualificationAfterPreviewIsRejectedForWholeBatch()throws Exception{
        var input=input();String p=previewId(input);long scopes=count("management_scope"),appointments=count("admin_appointment");
        jdbc.update("UPDATE admin_account SET enabled=0 WHERE id=?",account);failure(save(input,p,id()),409,"PREVIEW_STALE");
        assertThat(count("management_scope")).isEqualTo(scopes);assertThat(count("admin_appointment")).isEqualTo(appointments);
    }
    @Test void successfulIdempotencyReplaysAfterExpiryWithoutDuplicatingOrBypassingCurrentIdentity()throws Exception{
        var input=input();String p=previewId(input),key=id();var first=save(input,p,key);assertThat(first.statusCode()).as(first.body()).isEqualTo(201);
        long scopes=count("management_scope"),appointments=count("admin_appointment"),audit=count("audit_event");
        expire(p);
        var replay=save(input,p,key);assertThat(replay.statusCode()).isEqualTo(201);assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");assertThat(body(replay)).isEqualTo(body(first));
        var changed=new TreeMap<>(input);changed.put("reason","Different request");failure(save(changed,p,key),409,"IDEMPOTENCY_CONFLICT");
        jdbc.update("UPDATE admin_account SET enabled=0 WHERE id=?",superAccount);
        try{failure(save(input,p,key),401,"SESSION_REVOKED");}finally{jdbc.update("UPDATE admin_account SET enabled=1 WHERE id=?",superAccount);}
        assertThat(count("management_scope")).isEqualTo(scopes);assertThat(count("admin_appointment")).isEqualTo(appointments);assertThat(count("audit_event")).isEqualTo(audit);
    }
    @Test void onePreviewCannotCreateTwiceUsingDifferentIdempotencyKeys()throws Exception{
        var input=input();String p=previewId(input);assertThat(save(input,p,id()).statusCode()).isEqualTo(201);long scopes=count("management_scope");
        failure(save(input,p,id()),409,"STATE_CONFLICT");assertThat(count("management_scope")).isEqualTo(scopes);
    }
    @Test void successAuditFailureRollsBackScopeSystemsAppointmentsAndCompletion()throws Exception{
        var input=input();String p=previewId(input),key=id();long scopes=count("management_scope"),appointments=count("admin_appointment"),systems=count("scope_system"),audit=count("audit_event");
        db.execute("CREATE TRIGGER be09_fail_audit BEFORE INSERT ON audit_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='BE09 injected audit failure'");
        try{assertThat(save(input,p,key).statusCode()).isEqualTo(500);}finally{db.execute("DROP TRIGGER be09_fail_audit");}
        assertThat(count("management_scope")).isEqualTo(scopes);assertThat(count("admin_appointment")).isEqualTo(appointments);assertThat(count("scope_system")).isEqualTo(systems);assertThat(count("audit_event")).isEqualTo(audit);
        assertThat(jdbc.queryForObject("SELECT state FROM http_idempotency WHERE idempotency_key=?",String.class,key)).isEqualTo("IN_PROGRESS");
    }
    void expire(String preview){jdbc.update("UPDATE impact_preview SET created_at=UTC_TIMESTAMP(6)-INTERVAL 6 MINUTE,expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",preview);}
    @Test void concurrentSameKeyCreatesOneAggregateAndCanReplay()throws Exception{
        var input=input();String p=previewId(input),key=id();long scopes=count("management_scope"),appointments=count("admin_appointment");
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var one=pool.submit(()->{start.await();return save(input,p,key);});var two=pool.submit(()->{start.await();return save(input,p,key);});start.countDown();
            var a=one.get(30,TimeUnit.SECONDS);var b=two.get(30,TimeUnit.SECONDS);
            assertThat(List.of(a.statusCode(),b.statusCode())).contains(201).allMatch(status->status==201||status==409);
            for(var response:List.of(a,b))if(response.statusCode()==409)failure(response,409,"REQUEST_IN_PROGRESS");
        }
        var replay=save(input,p,key);assertThat(replay.statusCode()).isEqualTo(201);assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(count("management_scope")).isEqualTo(scopes+1);assertThat(count("admin_appointment")).isEqualTo(appointments+1);
    }
    @Test void qualificationCommittedAheadOfGuardedSaveCannotBeBypassed()throws Exception{
        var input=input();String p=previewId(input);long scopes=count("management_scope");
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var jwt=app.getBean(com.adgn.backend.identity.security.IdentityTokens.class).verify(token,com.adgn.backend.identity.security.IdentityTokens.Family.ADMIN);
        try(var pool=Executors.newFixedThreadPool(2)){
            var writer=pool.submit(()->app.getBean(com.adgn.backend.core.transaction.CommandBoundary.class).execute(
                    ()->app.getBean(com.adgn.backend.query.management.ManagementAccess.class).global(jwt).identity(),context->{
                        jdbc.update("UPDATE employment SET status='ENDED',recorded_end_at=UTC_TIMESTAMP(6),version=version+1 WHERE employee_id=?",employee);
                        entered.countDown();assertThat(release.await(20,TimeUnit.SECONDS)).isTrue();return null;
                    }));
            try{
                assertThat(entered.await(20,TimeUnit.SECONDS)).isTrue();var save=pool.submit(()->save(input,p,id()));release.countDown();writer.get(25,TimeUnit.SECONDS);
                failure(save.get(25,TimeUnit.SECONDS),409,"PREVIEW_STALE");
            }finally{release.countDown();}
        }
        assertThat(count("management_scope")).isEqualTo(scopes);
    }
}
