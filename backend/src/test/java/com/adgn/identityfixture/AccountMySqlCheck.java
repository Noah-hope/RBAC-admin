package com.adgn.identityfixture;

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
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountMySqlCheck {
    private IsolatedMySqlDatabase database;
    private ConfigurableApplicationContext application;
    private JdbcTemplate jdbc;
    private Path keyDirectory;
    private String base,superAccount,superToken,hash;
    private final String password="Temporary-Account-123456";
    private final JsonMapper json=JsonMapper.builder().build();
    private final HttpClient http=com.adgn.backend.testsupport.ContractHttpClient.create();
    @BeforeAll void start() throws Exception {
        keyDirectory=Files.createTempDirectory(Path.of(".tmp"),"be07-keys-");
        for(String family:List.of("admin","business","oa")) {
            var key=new RSAKeyGenerator(2048).keyID(family).generate();
            Files.writeString(keyDirectory.resolve(family+".json"),new JWKSet(family.equals("admin")?key:key.toPublicJWK()).toString(family.equals("admin")?false:true));
        }
        byte[] hmac=new byte[32];new java.security.SecureRandom().nextBytes(hmac);
        database=new IsolatedMySqlDatabase();
        application=database.startWeb("--rbac.identity.enabled=true","--rbac.identity.admin-key-id=admin",
                "--rbac.identity.admin-jwk-set="+keyDirectory.resolve("admin.json").toAbsolutePath(),
                "--rbac.identity.business-jwk-set="+keyDirectory.resolve("business.json").toAbsolutePath(),
                "--rbac.identity.oa-jwk-set="+keyDirectory.resolve("oa.json").toAbsolutePath(),
                "--rbac.identity.login-ip-limit=1000","--rbac.identity.login-account-limit=1000",
                "--rbac.idempotency.enabled=true","--rbac.idempotency.current-key-id=fixture",
                "--rbac.idempotency.keys-json="+json.writeValueAsString(Map.of("fixture",Base64.getEncoder().encodeToString(hmac))));
        jdbc=new JdbcTemplate(application.getBean(DataSource.class));
        base="http://127.0.0.1:"+((WebServerApplicationContext)application).getWebServer().getPort();
        hash=new PasswordPolicy().encode(password);
        superAccount=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO admin_account(id,login_name,password_hash,is_super_admin,must_change_password,created_at) VALUES(?,?,?,1,0,UTC_TIMESTAMP(6))",superAccount,superAccount,hash);
        superToken=login(superAccount,password);
    }
    @AfterAll void close() throws Exception {
        try { if(application!=null)application.close(); }
        finally {
            try { if(database!=null)database.close(); }
            finally { if(keyDirectory!=null){for(String family:List.of("admin","business","oa"))Files.deleteIfExists(keyDirectory.resolve(family+".json"));Files.deleteIfExists(keyDirectory);} }
        }
    }
    private String employee() {
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO employee(id,employee_key,name,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",id,id,"Fixture employee");return id;
    }
    private Map<String,Object> createBody(String employee,String login) {
        return Map.of("employeeId",employee,"loginName",login,"temporaryPassword",password,"confirmPassword",password,"reason","Fixture account operation");
    }
    private HttpResponse<String> request(String method,String path,Object body,String token,String key) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(25));
        if(token!=null)builder.header("Authorization","Bearer "+token);
        if(key!=null)builder.header("Idempotency-Key",key);
        if(body!=null)builder.header("Content-Type","application/json");
        return http.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    private JsonNode body(HttpResponse<String> response){return json.readTree(response.body());}
    private String login(String account,String value) throws Exception {
        var response=request("POST","/api/v1/admin/sessions",Map.of("loginName",account,"password",value),null,null);
        assertThat(response.statusCode()).isEqualTo(201);return body(response).path("accessToken").stringValue();
    }
    @Test void createReturnsSafeAccountAndCommitsAuditAndIdempotencyWithoutAppointmentOrSession() throws Exception {
        String employee=employee(),login="new-"+UUID.randomUUID();
        var response=request("POST","/api/v1/admin/accounts",createBody(employee,login),superToken,UUID.randomUUID().toString());
        assertThat(response.statusCode()).isEqualTo(201);
        String id=body(response).path("id").stringValue();
        assertThat(body(response).propertyNames()).containsExactlyInAnyOrder("id","employeeId","loginName","enabled","mustChangePassword","version","createdAt");
        assertThat(body(response).path("mustChangePassword").booleanValue()).isTrue();
        assertThat(response.headers().firstValue("Location")).contains("/api/v1/admin/accounts/"+id);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_session WHERE account_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_appointment WHERE account_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_subject WHERE subject_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(response.body()).doesNotContain(password,hash);
        for(String projection:jdbc.queryForList("SELECT safe_response FROM http_idempotency WHERE resource_location=?",String.class,"/api/v1/admin/accounts/"+id))assertThat(projection).doesNotContain(password,hash);
    }
    private String fixtureAccount(String login) {
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO admin_account(id,employee_id,login_name,password_hash,must_change_password,created_at) VALUES(?,?,?,?,0,UTC_TIMESTAMP(6))",id,employee(),login,hash);
        return id;
    }
    private void appoint(String account) {
        String employee=jdbc.queryForObject("SELECT employee_id FROM admin_account WHERE id=?",String.class,account);
        String company=UUID.randomUUID().toString(),department=UUID.randomUUID().toString(),position=UUID.randomUUID().toString(),scope=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO company(id,code,name,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",company,company,"Fixture company");
        jdbc.update("INSERT INTO department(id,company_id,code,name,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6))",department,company,department,"Fixture department");
        jdbc.update("INSERT INTO `position`(id,department_id,code,name,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6))",position,department,position,"Fixture position");
        jdbc.update("INSERT INTO employment(id,employee_id,company_id,department_id,position_id,created_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))",UUID.randomUUID().toString(),employee,company,department,position);
        jdbc.update("INSERT INTO management_scope(id,company_id,department_id,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",scope,company,department);
        jdbc.update("INSERT INTO admin_appointment(id,scope_id,account_id,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",UUID.randomUUID().toString(),scope,account);
    }
    private String key(){return UUID.randomUUID().toString();}
    private String path(String id,String suffix){return "/api/v1/admin/accounts/"+id+suffix;}
    private Map<String,Object> state(long version,boolean enabled){return Map.of("expectedVersion",version,"enabled",enabled,"reason","Fixture state operation");}
    private Map<String,Object> reset(long version){return Map.of("expectedVersion",version,"temporaryPassword","  Replacement-123456  ","confirmPassword","  Replacement-123456  ","reason","Fixture reset operation");}
    @Test void listSupportsLiteralPrefixEmployeeEnabledAndStablePaginationWithSafeDetails() throws Exception {
        String prefix="page"+key().replace("-","");
        String first=fixtureAccount(prefix+"_first"),second=fixtureAccount(prefix+"Xsecond");
        var filtered=request("GET","/api/v1/admin/accounts?q="+prefix+"_&pageSize=10",null,superToken,null);
        assertThat(filtered.statusCode()).isEqualTo(200);
        assertThat(body(filtered).path("total").longValue()).isEqualTo(1);
        assertThat(body(filtered).path("items").get(0).path("id").stringValue()).isEqualTo(first);
        String employee=jdbc.queryForObject("SELECT employee_id FROM admin_account WHERE id=?",String.class,second);
        var byEmployee=request("GET","/api/v1/admin/accounts?employeeId="+employee+"&enabled=true",null,superToken,null);
        assertThat(byEmployee.statusCode()).isEqualTo(200);assertThat(body(byEmployee).path("total").longValue()).isEqualTo(1);
        var beyond=request("GET","/api/v1/admin/accounts?q="+prefix+"&page=2&pageSize=10",null,superToken,null);
        assertThat(body(beyond).path("items").isEmpty()).isTrue();assertThat(body(beyond).path("total").longValue()).isEqualTo(2);
        var detail=request("GET",path(first,""),null,superToken,null);
        assertThat(detail.statusCode()).isEqualTo(200);assertThat(detail.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(detail.body()).doesNotContain(hash,password,"credentialVersion","sessionEpoch");
        assertThat(request("GET",path(key(),""),null,superToken,null).statusCode()).isEqualTo(404);
    }
    @Test void onlyCurrentSuperAdminCanManageAccountsAndOrdinaryDetailsAreHidden() throws Exception {
        String account=fixtureAccount(key());appoint(account);String ordinary=login(jdbc.queryForObject("SELECT login_name FROM admin_account WHERE id=?",String.class,account),password);
        assertThat(request("GET","/api/v1/admin/accounts",null,ordinary,null).statusCode()).isEqualTo(403);
        assertThat(request("GET",path(superAccount,""),null,ordinary,null).statusCode()).isEqualTo(404);
        assertThat(request("GET",path(key(),""),null,ordinary,null).statusCode()).isEqualTo(404);
        assertThat(request("POST","/api/v1/admin/accounts",createBody(employee(),"denied"+key()),ordinary,key()).statusCode()).isEqualTo(403);
        assertThat(request("PUT",path(account,"/state"),state(1,false),ordinary,key()).statusCode()).isEqualTo(403);
        assertThat(request("PUT",path(account,"/password"),reset(1),ordinary,key()).statusCode()).isEqualTo(403);
    }
    @Test void createBindsBothUniqueKeysAndRejectsInactiveOrMissingEmployees() throws Exception {
        String employee=employee(),login="unique"+key();
        assertThat(request("POST","/api/v1/admin/accounts",createBody(employee,login),superToken,key()).statusCode()).isEqualTo(201);
        assertThat(request("POST","/api/v1/admin/accounts",createBody(employee,"other"+key()),superToken,key()).statusCode()).isEqualTo(409);
        assertThat(request("POST","/api/v1/admin/accounts",createBody(employee(),login),superToken,key()).statusCode()).isEqualTo(409);
        String inactive=employee();jdbc.update("UPDATE employee SET status='INACTIVE' WHERE id=?",inactive);
        for(String invalid:List.of(inactive,key()))assertThat(request("POST","/api/v1/admin/accounts",createBody(invalid,"missing"+key()),superToken,key()).statusCode()).isEqualTo(422);
        assertThat(request("POST","/api/v1/admin/sessions",Map.of("loginName",login,"password",password),null,null).statusCode()).isEqualTo(401);
    }
    @Test void creationReplaysOnlySameContentWithoutSecretsAndFreshRequestIdsAreUsed() throws Exception {
        String key=key(),employee=employee(),login="replay"+key;
        var input=createBody(employee,login);
        var first=request("POST","/api/v1/admin/accounts",input,superToken,key);
        var second=request("POST","/api/v1/admin/accounts",input,superToken,key);
        assertThat(first.statusCode()).isEqualTo(201);assertThat(second.statusCode()).isEqualTo(201);
        assertThat(body(second)).isEqualTo(body(first));assertThat(second.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(second.headers().firstValue("X-Request-Id")).isNotEqualTo(first.headers().firstValue("X-Request-Id"));
        var changed=new HashMap<>(input);changed.put("temporaryPassword","Another-Temp-123456");changed.put("confirmPassword","Another-Temp-123456");
        var conflict=request("POST","/api/v1/admin/accounts",changed,superToken,key);
        assertThat(conflict.statusCode()).isEqualTo(409);assertThat(body(conflict).path("code").stringValue()).isEqualTo("IDEMPOTENCY_CONFLICT");
        String id=body(first).path("id").stringValue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE event_type='ADMIN_ACCOUNT_CREATED' AND target_id=?",Integer.class,id)).isEqualTo(1);
        for(String projection:jdbc.queryForList("SELECT safe_response FROM http_idempotency WHERE principal_id=? AND idempotency_key=?",String.class,superAccount,key))assertThat(projection).doesNotContain(password,hash);
        assertThat(jdbc.queryForObject("SELECT event_snapshot FROM audit_event WHERE event_type='ADMIN_ACCOUNT_CREATED' AND target_id=?",String.class,id)).doesNotContain(password,hash);
    }
    @Test void disableThenEnableNeverRevivesOldSessionsOrRemovesAppointments() throws Exception {
        String account=fixtureAccount(key());appoint(account);
        String login=jdbc.queryForObject("SELECT login_name FROM admin_account WHERE id=?",String.class,account);
        String first=login(login,password),second=login(login,password);
        var disabled=request("PUT",path(account,"/state"),state(1,false),superToken,key());
        assertThat(disabled.statusCode()).isEqualTo(200);assertThat(body(disabled).path("version").longValue()).isEqualTo(2);
        for(String token:List.of(first,second))assertThat(request("GET","/api/v1/admin/sessions/current",null,token,null).statusCode()).isEqualTo(401);
        assertThat(request("PUT",path(account,"/state"),state(2,true),superToken,key()).statusCode()).isEqualTo(200);
        assertThat(request("GET","/api/v1/admin/sessions/current",null,first,null).statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_appointment WHERE account_id=? AND status='ACTIVE'",Integer.class,account)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT session_epoch FROM admin_account WHERE id=?",Long.class,account)).isEqualTo(2);
        assertThat(login(login,password)).isNotBlank();
    }
    @Test void sameStateDoesNotAdvanceVersionsOrAuditButStaleVersionIsRejected() throws Exception {
        String account=fixtureAccount(key());
        var unchanged=request("PUT",path(account,"/state"),state(1,true),superToken,key());
        assertThat(unchanged.statusCode()).isEqualTo(200);assertThat(body(unchanged).path("version").longValue()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE target_id=?",Integer.class,account)).isZero();
        var stale=request("PUT",path(account,"/state"),state(0,false),superToken,key());
        assertThat(stale.statusCode()).isEqualTo(409);assertThat(body(stale).path("code").stringValue()).isEqualTo("VERSION_CONFLICT");
    }
    @Test void replayDoesNotRecheckTheOriginalVersionAfterLaterStateChanges() throws Exception {
        String account=fixtureAccount(key()),key=key();
        var first=request("PUT",path(account,"/state"),state(1,false),superToken,key);
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(request("PUT",path(account,"/state"),state(2,true),superToken,key()).statusCode()).isEqualTo(200);
        var replay=request("PUT",path(account,"/state"),state(1,false),superToken,key);
        assertThat(replay.statusCode()).isEqualTo(200);assertThat(body(replay)).isEqualTo(body(first));
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(jdbc.queryForObject("SELECT enabled FROM admin_account WHERE id=?",Boolean.class,account)).isTrue();
    }
    @Test void resetRevokesEverySessionAndRequiresPasswordChangeBeforeNormalAccess() throws Exception {
        String account=fixtureAccount(key());appoint(account);String login=jdbc.queryForObject("SELECT login_name FROM admin_account WHERE id=?",String.class,account);
        String first=login(login,password),second=login(login,password);
        var response=request("PUT",path(account,"/password"),reset(1),superToken,key());
        assertThat(response.statusCode()).isEqualTo(200);assertThat(body(response).path("mustChangePassword").booleanValue()).isTrue();
        assertThat(response.body()).doesNotContain("Replacement-123456",hash);
        for(String token:List.of(first,second))assertThat(request("GET","/api/v1/admin/sessions/current",null,token,null).statusCode()).isEqualTo(401);
        String restricted=login(login,"  Replacement-123456  ");
        assertThat(request("GET","/api/v1/admin/accounts",null,restricted,null).statusCode()).isEqualTo(403);
        assertThat(request("PUT","/api/v1/admin/password",Map.of("currentPassword","  Replacement-123456  ","newPassword","Final-Own-Password-123456","confirmPassword","Final-Own-Password-123456"),restricted,null).statusCode()).isEqualTo(204);
        assertThat(login(login,"Final-Own-Password-123456")).isNotBlank();
    }
    @Test void resetDoesNotEnableDisabledAccountOrRestoreMissingQualificationAndProtectsSuperAdmin() throws Exception {
        String account=fixtureAccount(key());
        assertThat(request("PUT",path(account,"/state"),state(1,false),superToken,key()).statusCode()).isEqualTo(200);
        var response=request("PUT",path(account,"/password"),reset(2),superToken,key());
        assertThat(response.statusCode()).isEqualTo(200);assertThat(body(response).path("enabled").booleanValue()).isFalse();
        assertThat(request("PUT",path(account,"/state"),state(3,true),superToken,key()).statusCode()).isEqualTo(200);
        String login=jdbc.queryForObject("SELECT login_name FROM admin_account WHERE id=?",String.class,account);
        assertThat(request("POST","/api/v1/admin/sessions",Map.of("loginName",login,"password","  Replacement-123456  "),null,null).statusCode()).isEqualTo(401);
        assertThat(request("PUT",path(superAccount,"/state"),state(1,false),superToken,key()).statusCode()).isEqualTo(403);
        assertThat(request("PUT",path(superAccount,"/password"),reset(1),superToken,key()).statusCode()).isEqualTo(403);
    }
    @Test void accountInputBoundariesRejectMissingKeysWrongVersionsAndCredentialsWithoutLeaks() throws Exception {
        String employee=employee();var valid=createBody(employee,"valid"+key());
        assertThat(request("POST","/api/v1/admin/accounts",valid,superToken,null).statusCode()).isEqualTo(422);
        assertThat(request("POST","/api/v1/admin/accounts",valid,superToken,"invalid").statusCode()).isEqualTo(422);
        for(var change:List.of(Map.of("confirmPassword","Different-123456"),Map.of("temporaryPassword","short"),Map.of("loginName","bad space"),Map.of("reason"," "),Map.of("isSuperAdmin",true))) {
            var invalid=new HashMap<>(valid);invalid.putAll(change);
            var response=request("POST","/api/v1/admin/accounts",invalid,superToken,key());
            assertThat(response.statusCode()).isEqualTo(422);assertThat(response.body()).doesNotContain(password,hash);
        }
        for(String query:List.of("page=0","pageSize=11","enabled=1","q=","employeeId=bad%20id"))assertThat(request("GET","/api/v1/admin/accounts?"+query,null,superToken,null).statusCode()).isEqualTo(422);
        String account=fixtureAccount(key());
        for(Object invalid:List.of(Map.of("expectedVersion",-1,"enabled",false,"reason","fixture"),Map.of("expectedVersion",1.5,"enabled",false,"reason","fixture"),Map.of("expectedVersion",1,"reason","fixture")))
            assertThat(request("PUT",path(account,"/state"),invalid,superToken,key()).statusCode()).isEqualTo(422);
    }
    @Test void auditFailureRollsBackAccountCreationAndRetryRetainsRequestBinding() throws Exception {
        String employee=employee(),login="fault"+key(),key=key();var input=createBody(employee,login);
        database.execute("CREATE TRIGGER be07_audit_failure BEFORE INSERT ON audit_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure'");
        try { assertThat(request("POST","/api/v1/admin/accounts",input,superToken,key).statusCode()).isEqualTo(500); }
        finally { database.execute("DROP TRIGGER be07_audit_failure"); }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_account WHERE employee_id=?",Integer.class,employee)).isZero();
        assertThat(jdbc.queryForObject("SELECT state FROM http_idempotency WHERE principal_id=? AND idempotency_key=?",String.class,superAccount,key)).isEqualTo("IN_PROGRESS");
        var changed=new HashMap<>(input);changed.put("loginName","changed"+key());
        var conflict=request("POST","/api/v1/admin/accounts",changed,superToken,key);
        assertThat(body(conflict).path("code").stringValue()).isEqualTo("IDEMPOTENCY_CONFLICT");
        jdbc.update("UPDATE http_idempotency SET lease_until=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE principal_id=? AND idempotency_key=?",superAccount,key);
        assertThat(request("POST","/api/v1/admin/accounts",input,superToken,key).statusCode()).isEqualTo(201);
    }
    @Test void resultSaveFailureRollsBackStateAndAuditWhileKeepingReservation() throws Exception {
        String account=fixtureAccount(key());appoint(account);String login=jdbc.queryForObject("SELECT login_name FROM admin_account WHERE id=?",String.class,account);String token=login(login,password),key=key();
        database.execute("CREATE TRIGGER be07_result_failure BEFORE UPDATE ON http_idempotency FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure'");
        try { assertThat(request("PUT",path(account,"/state"),state(1,false),superToken,key).statusCode()).isEqualTo(500); }
        finally { database.execute("DROP TRIGGER be07_result_failure"); }
        assertThat(jdbc.queryForObject("SELECT enabled FROM admin_account WHERE id=?",Boolean.class,account)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE target_id=? AND event_type='ADMIN_ACCOUNT_DISABLED'",Integer.class,account)).isZero();
        assertThat(request("GET","/api/v1/admin/sessions/current",null,token,null).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT state FROM http_idempotency WHERE principal_id=? AND idempotency_key=?",String.class,superAccount,key)).isEqualTo("IN_PROGRESS");
    }
    @Test void resetAuditFailureLeavesHashVersionsAndSessionsUnchanged() throws Exception {
        String account=fixtureAccount(key());appoint(account);String login=jdbc.queryForObject("SELECT login_name FROM admin_account WHERE id=?",String.class,account);String token=login(login,password);
        database.execute("CREATE TRIGGER be07_reset_failure BEFORE INSERT ON audit_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure'");
        try { assertThat(request("PUT",path(account,"/password"),reset(1),superToken,key()).statusCode()).isEqualTo(500); }
        finally { database.execute("DROP TRIGGER be07_reset_failure"); }
        assertThat(jdbc.queryForObject("SELECT password_hash FROM admin_account WHERE id=?",String.class,account)).isEqualTo(hash);
        assertThat(jdbc.queryForObject("SELECT credential_version FROM admin_account WHERE id=?",Long.class,account)).isEqualTo(1);
        assertThat(request("GET","/api/v1/admin/sessions/current",null,token,null).statusCode()).isEqualTo(200);
    }
    @Test void simultaneousDuplicateCreationCommitsOneAccountAndOneAudit() throws Exception {
        String employee=employee(),login="parallel"+key(),key=key();var input=createBody(employee,login);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(4);var ready=new java.util.concurrent.CountDownLatch(4);var go=new java.util.concurrent.CountDownLatch(1);
        try {
            var futures=new ArrayList<java.util.concurrent.Future<HttpResponse<String>>>();
            for(int i=0;i<4;i++)futures.add(pool.submit(()->{ready.countDown();if(!go.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("fixture barrier");return request("POST","/api/v1/admin/accounts",input,superToken,key);}));
            assertThat(ready.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();go.countDown();
            for(var future:futures)assertThat(future.get(30,java.util.concurrent.TimeUnit.SECONDS).statusCode()).isIn(201,409);
            var replay=request("POST","/api/v1/admin/accounts",input,superToken,key);assertThat(replay.statusCode()).isEqualTo(201);
            String id=body(replay).path("id").stringValue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_account WHERE employee_id=?",Integer.class,employee)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE target_id=? AND event_type='ADMIN_ACCOUNT_CREATED'",Integer.class,id)).isEqualTo(1);
        } finally { go.countDown();pool.shutdownNow(); }
    }
    @Test void revokedCallerCannotReplayPreviouslyCompletedAccountResult() throws Exception {
        String caller=key();jdbc.update("INSERT INTO admin_account(id,login_name,password_hash,is_super_admin,must_change_password,created_at) VALUES(?,?,?,1,0,UTC_TIMESTAMP(6))",caller,caller,hash);
        String token=login(caller,password),key=key();var input=createBody(employee(),"revoked"+key());
        var first=request("POST","/api/v1/admin/accounts",input,token,key);assertThat(first.statusCode()).isEqualTo(201);
        jdbc.update("UPDATE admin_account SET enabled=0,session_epoch=session_epoch+1 WHERE id=?",caller);
        var denied=request("POST","/api/v1/admin/accounts",input,token,key);
        assertThat(denied.statusCode()).isEqualTo(401);assertThat(denied.headers().firstValue("Location")).isEmpty();
        assertThat(denied.body()).doesNotContain(body(first).path("id").stringValue(),password);
    }
    @Test void lostGlobalAuthorityCannotReplayEvenWithAnOtherwiseValidOrdinarySession() throws Exception {
        String caller=fixtureAccount(key());appoint(caller);jdbc.update("UPDATE admin_account SET is_super_admin=1 WHERE id=?",caller);
        String login=jdbc.queryForObject("SELECT login_name FROM admin_account WHERE id=?",String.class,caller);
        String token=login(login,password),key=key();var input=createBody(employee(),"global"+key());
        var first=request("POST","/api/v1/admin/accounts",input,token,key);assertThat(first.statusCode()).isEqualTo(201);
        jdbc.update("UPDATE admin_account SET is_super_admin=0 WHERE id=?",caller);
        var denied=request("POST","/api/v1/admin/accounts",input,token,key);
        assertThat(denied.statusCode()).isEqualTo(403);assertThat(denied.headers().firstValue("Location")).isEmpty();
        assertThat(denied.body()).doesNotContain(body(first).path("id").stringValue());
        assertThat(request("GET","/api/v1/admin/sessions/current",null,token,null).statusCode()).isEqualTo(200);
    }
    @Test void resetReplayDoesNotHashOrAdvanceCredentialsTwiceAndCachedErrorsUseCurrentRequestId() throws Exception {
        String account=fixtureAccount(key()),key=key();
        var first=request("PUT",path(account,"/password"),reset(1),superToken,key);
        assertThat(first.statusCode()).isEqualTo(200);
        String saved=jdbc.queryForObject("SELECT password_hash FROM admin_account WHERE id=?",String.class,account);
        var replay=request("PUT",path(account,"/password"),reset(1),superToken,key);
        assertThat(replay.statusCode()).isEqualTo(200);assertThat(body(replay)).isEqualTo(body(first));
        assertThat(jdbc.queryForObject("SELECT credential_version FROM admin_account WHERE id=?",Long.class,account)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM admin_account WHERE id=?",String.class,account)).isEqualTo(saved);
        String staleKey=key();var stale=request("PUT",path(account,"/state"),state(1,false),superToken,staleKey);
        var staleReplay=request("PUT",path(account,"/state"),state(1,false),superToken,staleKey);
        assertThat(staleReplay.statusCode()).isEqualTo(409);assertThat(staleReplay.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(body(staleReplay).path("requestId").stringValue()).isEqualTo(staleReplay.headers().firstValue("X-Request-Id").orElseThrow());
        assertThat(body(staleReplay).path("requestId")).isNotEqualTo(body(stale).path("requestId"));
    }
    @Test void countersAtSafeIntegerLimitRejectBeforeAnyWrite() throws Exception {
        String account=fixtureAccount(key());jdbc.update("UPDATE admin_account SET version=9007199254740991 WHERE id=?",account);
        var denied=request("PUT",path(account,"/state"),state(9007199254740991L,false),superToken,key());
        assertThat(denied.statusCode()).isEqualTo(409);assertThat(body(denied).path("code").stringValue()).isEqualTo("STATE_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT enabled FROM admin_account WHERE id=?",Boolean.class,account)).isTrue();
        jdbc.update("UPDATE admin_account SET version=1,credential_version=9007199254740991 WHERE id=?",account);
        assertThat(request("PUT",path(account,"/password"),reset(1),superToken,key()).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM admin_account WHERE id=?",String.class,account)).isEqualTo(hash);
    }
    @Test void guardedQualificationRejectsCallerRevokedWhileWaitingForMutationGuard() throws Exception {
        String caller=key();jdbc.update("INSERT INTO admin_account(id,login_name,password_hash,is_super_admin,must_change_password,created_at) VALUES(?,?,?,1,0,UTC_TIMESTAMP(6))",caller,caller,hash);
        String token=login(caller,password),employee=employee();var input=createBody(employee,"waiting"+key());
        var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
        try(var connection=database.runtimeConnection()) {
            connection.setAutoCommit(false);
            try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT revision FROM mutation_guard WHERE id=1 FOR UPDATE")){assertThat(rows.next()).isTrue();}
            var pending=pool.submit(()->request("POST","/api/v1/admin/accounts",input,token,key()));
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(4);boolean waiting=false;
            while(System.nanoTime()<deadline){if(!database.scalar("SELECT COUNT(*) FROM performance_schema.data_lock_waits").equals("0")){waiting=true;break;}Thread.sleep(20);}
            assertThat(waiting).as("request waits for the real guard row").isTrue();
            try(var statement=connection.prepareStatement("UPDATE admin_account SET enabled=0,session_epoch=session_epoch+1 WHERE id=?")){statement.setString(1,caller);statement.executeUpdate();}
            connection.commit();assertThat(pending.get(10,java.util.concurrent.TimeUnit.SECONDS).statusCode()).isEqualTo(401);
        } finally { pool.shutdownNow(); }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_account WHERE employee_id=?",Integer.class,employee)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM http_idempotency WHERE principal_id=?",Integer.class,caller)).isZero();
    }
    @Test void disablingIdempotencyFailsClosedForWritesButKeepsAuthorizedReadsAvailable() throws Exception {
        String original=base;
        try(var disabled=database.startWeb("--rbac.identity.enabled=true","--rbac.identity.admin-key-id=admin",
                "--rbac.identity.admin-jwk-set="+keyDirectory.resolve("admin.json").toAbsolutePath(),
                "--rbac.identity.business-jwk-set="+keyDirectory.resolve("business.json").toAbsolutePath(),
                "--rbac.identity.oa-jwk-set="+keyDirectory.resolve("oa.json").toAbsolutePath(),"--rbac.idempotency.enabled=false")){
            base="http://127.0.0.1:"+((WebServerApplicationContext)disabled).getWebServer().getPort();
            String employee=employee();
            assertThat(request("POST","/api/v1/admin/accounts",createBody(employee,"closed"+key()),superToken,key()).statusCode()).isEqualTo(503);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_account WHERE employee_id=?",Integer.class,employee)).isZero();
            assertThat(request("GET","/api/v1/admin/accounts",null,superToken,null).statusCode()).isEqualTo(200);
        } finally { base=original; }
    }
}
