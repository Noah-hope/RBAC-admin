package com.adgn.identityfixture;

import com.adgn.backend.testsupport.IsolatedMySqlDatabase;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.*;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;
import javax.sql.DataSource;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Real servlet + real disposable MySQL. No test endpoint is shipped in the application. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdentityMySqlCheck {
    private IsolatedMySqlDatabase database;
    private ConfigurableApplicationContext application;
    private JdbcTemplate jdbc;
    private String base;
    private final HttpClient http = com.adgn.backend.testsupport.ContractHttpClient.create();
    private final JsonMapper json = JsonMapper.builder().build();
    private final String password = "  初始口令𠮷-" + UUID.randomUUID();
    private String passwordHash;
    private Path keysDirectory;
    private com.nimbusds.jose.jwk.RSAKey businessSigningKey,oaSigningKey;

    @BeforeAll void start() throws Exception {
        var directory = Files.createTempDirectory(Path.of(".tmp"), "be06-keys-");keysDirectory=directory;
        var admin = new RSAKeyGenerator(2048).keyID("admin-1").generate();
        var business = new RSAKeyGenerator(2048).keyID("business-1").generate();
        var oa = new RSAKeyGenerator(2048).keyID("oa-1").generate();
        businessSigningKey=business;oaSigningKey=oa;
        var adminFile = directory.resolve("admin.json");
        var businessFile = directory.resolve("business.json");
        var oaFile = directory.resolve("oa.json");
        Files.writeString(adminFile, new com.nimbusds.jose.jwk.JWKSet(admin).toString(false));
        Files.writeString(businessFile, new com.nimbusds.jose.jwk.JWKSet(business.toPublicJWK()).toString());
        Files.writeString(oaFile, new com.nimbusds.jose.jwk.JWKSet(oa.toPublicJWK()).toString());
        var encoder = new Pbkdf2PasswordEncoder("", 16, 600000, 256);
        encoder.setAlgorithm(Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        passwordHash = "{pbkdf2-v1}" + encoder.encode(password);
        database = new IsolatedMySqlDatabase();
        application = database.startWeb("--rbac.identity.enabled=true", "--rbac.identity.admin-key-id=admin-1",
                "--rbac.identity.admin-jwk-set=" + adminFile.toAbsolutePath(),
                "--rbac.identity.business-jwk-set=" + businessFile.toAbsolutePath(),
                "--rbac.identity.oa-jwk-set=" + oaFile.toAbsolutePath(), "--rbac.identity.login-ip-limit=1000");
        jdbc = new JdbcTemplate(application.getBean(DataSource.class));
        base = "http://127.0.0.1:" + ((WebServerApplicationContext)application).getWebServer().getPort();
    }
    @AfterAll void close() throws Exception {
        if (application != null) application.close();
        if (database != null) database.close();
        if(keysDirectory!=null) {
            for(var file:List.of("admin.json","business.json","oa.json"))Files.deleteIfExists(keysDirectory.resolve(file));
            Files.deleteIfExists(keysDirectory);
        }
    }
    private String account(boolean restricted) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO admin_account(id,login_name,password_hash,is_super_admin,must_change_password,created_at) VALUES(?,?,?,1,?,UTC_TIMESTAMP(6))",
                id, id, passwordHash, restricted ? 1 : 0);
        return id;
    }
    private HttpResponse<String> request(String method, String path, Object body, String token) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(base+path)).timeout(java.time.Duration.ofSeconds(15));
        if (token!=null) builder.header("Authorization", "Bearer " + token);
        if (body!=null) builder.header("Content-Type", "application/json");
        return http.send(builder.method(method, body==null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> login(String account) throws Exception {
        return request("POST", "/api/v1/admin/sessions", Map.of("loginName", account,"password",password), null);
    }
    private JsonNode body(HttpResponse<String> response) { return json.readTree(response.body()); }

    @Test void corruptedReadGuardCannotAuthenticateOrExposeTheSession() throws Exception {
        String account=account(false),token=token(account);
        long revision=jdbc.queryForObject("SELECT revision FROM mutation_guard WHERE id=1",Long.class);
        jdbc.update("DELETE FROM mutation_guard WHERE id=1");
        try {
            var denied=request("GET","/api/v1/admin/sessions/current",null,token);
            assertThat(denied.statusCode()).as(denied.body()).isEqualTo(500);
            assertThat(body(denied).path("code").stringValue()).isEqualTo("DATA_INTEGRITY_ERROR");
            assertThat(denied.headers().firstValue("Cache-Control")).contains("no-store");
            assertThat(denied.body()).doesNotContain(token,password,passwordHash,"SELECT","mutation_guard");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_session WHERE account_id=?",Long.class,account)).isEqualTo(1);
        } finally { jdbc.update("INSERT INTO mutation_guard(id,revision) VALUES(1,?)",revision); }
        assertThat(request("GET","/api/v1/admin/sessions/current",null,token).statusCode()).isEqualTo(200);
    }
    @Test void loginCreatesPersistentSessionAndOnlyReturnsTokenOnce() throws Exception {
        String account=account(false);
        var response=login(account);
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(response.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(response.headers().firstValue("Location")).contains("/api/v1/admin/sessions/current");
        var session=body(response);
        assertThat(session.path("identity").stringValue()).isEqualTo("SUPER_ADMIN");
        assertThat(session.path("mode").stringValue()).isEqualTo("NORMAL");
        assertThat(session.path("accountId").stringValue()).isEqualTo(account);
        String token=session.path("accessToken").stringValue();
        assertThat(token).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_session WHERE account_id=?", Integer.class,account)).isEqualTo(1);
        var current=request("GET","/api/v1/admin/sessions/current",null,token);
        assertThat(current.statusCode()).isEqualTo(200);
        assertThat(body(current).has("accessToken")).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE event_type='ADMIN_LOGIN' AND actor_id=?",Integer.class,account)).isEqualTo(1);
        for(String snapshot:jdbc.queryForList("SELECT event_snapshot FROM audit_event WHERE actor_id=?",String.class,account))
            assertThat(snapshot).doesNotContain(password,passwordHash,token);
    }

    private String token(String account) throws Exception {
        var response=login(account); assertThat(response.statusCode()).isEqualTo(201);
        return body(response).path("accessToken").stringValue();
    }
    @Test void logoutIsRepeatableAndDoesNotRevokeAnotherSession() throws Exception {
        String account=account(false), first=token(account), second=token(account);
        assertThat(request("DELETE","/api/v1/admin/sessions/current",null,"invalid").statusCode()).isEqualTo(204);
        assertThat(request("GET","/api/v1/admin/sessions/current",null,first).statusCode()).isEqualTo(200);
        assertThat(request("DELETE","/api/v1/admin/sessions/current",null,first).statusCode()).isEqualTo(204);
        assertThat(request("DELETE","/api/v1/admin/sessions/current",null,first).statusCode()).isEqualTo(204);
        var revoked=request("GET","/api/v1/admin/sessions/current",null,first);
        assertThat(revoked.statusCode()).isEqualTo(401);
        assertThat(body(revoked).path("code").stringValue()).isEqualTo("SESSION_REVOKED");
        assertThat(request("GET","/api/v1/admin/sessions/current",null,second).statusCode()).isEqualTo(200);
    }
    @Test void temporaryPasswordTokenCanOnlyChangePasswordOrLogout() throws Exception {
        String account=account(true), token=token(account);
        assertThat(request("GET","/api/v1/admin/sessions/current",null,token).statusCode()).isEqualTo(403);
        assertThat(request("GET","/api/v1/admin/accounts",null,token).statusCode()).isEqualTo(403);
        var changed=request("PUT","/api/v1/admin/password", Map.of("currentPassword",password,
                "newPassword","  修改后的𠮷口令-123456  ","confirmPassword","  修改后的𠮷口令-123456  "),token);
        assertThat(changed.statusCode()).isEqualTo(204);
        assertThat(changed.body()).isEmpty();
        assertThat(request("GET","/api/v1/admin/sessions/current",null,token).statusCode()).isEqualTo(401);
        var relogin=request("POST","/api/v1/admin/sessions",Map.of("loginName",account,"password","  修改后的𠮷口令-123456  "),null);
        assertThat(relogin.statusCode()).isEqualTo(201);
        assertThat(body(relogin).path("mode").stringValue()).isEqualTo("NORMAL");
    }
    @Test void passwordChangeRevokesEveryOldSessionAndRejectsOldPassword() throws Exception {
        String account=account(false), first=token(account), second=token(account);
        var changed=request("PUT","/api/v1/admin/password", Map.of("currentPassword",password,
                "newPassword","另一个完整新口令-123456", "confirmPassword","另一个完整新口令-123456"),first);
        assertThat(changed.statusCode()).isEqualTo(204);
        assertThat(request("GET","/api/v1/admin/sessions/current",null,second).statusCode()).isEqualTo(401);
        assertThat(login(account).statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT credential_version FROM admin_account WHERE id=?",Long.class,account)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT session_epoch FROM admin_account WHERE id=?",Long.class,account)).isEqualTo(2);
    }
    @Test void failedAuditRollsBackLoginAndPasswordChange() throws Exception {
        String account=account(false);
        database.execute("CREATE TRIGGER be06_fail_audit BEFORE INSERT ON audit_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure'");
        try {
            assertThat(login(account).statusCode()).isEqualTo(500);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_session WHERE account_id=?",Integer.class,account)).isZero();
        } finally { database.execute("DROP TRIGGER be06_fail_audit"); }
        String token=token(account);
        database.execute("CREATE TRIGGER be06_fail_audit BEFORE INSERT ON audit_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture failure'");
        try {
            assertThat(request("PUT","/api/v1/admin/password",Map.of("currentPassword",password,
                    "newPassword","新口令需要完整回滚-123456","confirmPassword","新口令需要完整回滚-123456"),token).statusCode()).isEqualTo(500);
        } finally { database.execute("DROP TRIGGER be06_fail_audit"); }
        assertThat(jdbc.queryForObject("SELECT credential_version FROM admin_account WHERE id=?",Long.class,account)).isEqualTo(1);
        assertThat(request("GET","/api/v1/admin/sessions/current",null,token).statusCode()).isEqualTo(200);
        assertThat(login(account).statusCode()).isEqualTo(201);
    }
    @Test void disabledAccountDoesNotReviveOldSessionAfterEnable() throws Exception {
        String account=account(false), token=token(account);
        jdbc.update("UPDATE admin_account SET enabled=0,session_epoch=session_epoch+1 WHERE id=?",account);
        assertThat(login(account).statusCode()).isEqualTo(401);
        assertThat(request("GET","/api/v1/admin/sessions/current",null,token).statusCode()).isEqualTo(401);
        jdbc.update("UPDATE admin_account SET enabled=1 WHERE id=?",account);
        assertThat(request("GET","/api/v1/admin/sessions/current",null,token).statusCode()).isEqualTo(401);
    }
    @Test void plainAccountWithoutCurrentAppointmentCannotLogin() throws Exception {
        String account=account(false);
        String employee=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO employee(id,employee_key,name,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",employee,employee,"测试员工");
        jdbc.update("UPDATE admin_account SET is_super_admin=0,employee_id=? WHERE id=?",employee,account);
        assertThat(login(account).statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_session WHERE account_id=?",Integer.class,account)).isZero();
    }
    @Test void invalidPasswordAndMissingAccountAreIndistinguishable() throws Exception {
        String account=account(false);
        var wrong=request("POST","/api/v1/admin/sessions",Map.of("loginName",account,"password","wrong"),null);
        var absent=login(UUID.randomUUID().toString());
        assertThat(wrong.statusCode()).isEqualTo(401); assertThat(absent.statusCode()).isEqualTo(401);
        assertThat(body(wrong).path("code").isString()).isTrue();
        assertThat(body(absent).path("code").isString()).isTrue();
        assertThat(body(wrong).path("code").stringValue()).isEqualTo("AUTHENTICATION_FAILED");
        assertThat(body(absent).path("code").stringValue()).isEqualTo("AUTHENTICATION_FAILED");
        assertThat(wrong.body()).doesNotContain(account,passwordHash,password);
    }
    @Test void adminTokenCannotAuthenticateBusinessOrOaAndTamperingFails() throws Exception {
        String token=token(account(false));
        assertThat(request("GET","/api/v1/integration/identity-probe",null,token).statusCode()).isEqualTo(401);
        assertThat(request("POST","/api/v1/oa/results",Map.of(),token).statusCode()).isEqualTo(401);
        String[] parts=token.split("\\.");
        String signature=parts[2];
        parts[2]=(signature.charAt(0)=='A' ? "B" : "A")+signature.substring(1);
        assertThat(request("GET","/api/v1/admin/sessions/current",null,String.join(".",parts)).statusCode()).isEqualTo(401);
    }
    @Test void passwordValidationIsUnicodeAwareAndHasNoSideEffects() throws Exception {
        String account=account(false), token=token(account);
        for (var input:List.of(Map.of("currentPassword",password,"newPassword",password,"confirmPassword",password),
                Map.of("currentPassword",password,"newPassword","𠮷".repeat(11),"confirmPassword","𠮷".repeat(11)),
                Map.of("currentPassword",password,"newPassword","a".repeat(129),"confirmPassword","a".repeat(129)),
                Map.of("currentPassword",password,"newPassword","abcdefghijkl","confirmPassword","abcdefghijklX"))) {
            assertThat(request("PUT","/api/v1/admin/password",input,token).statusCode()).isEqualTo(422);
        }
        assertThat(jdbc.queryForObject("SELECT credential_version FROM admin_account WHERE id=?",Long.class,account)).isEqualTo(1);
        assertThat(request("PUT","/api/v1/admin/password",Map.of("currentPassword",password,
                "newPassword","𠮷".repeat(128),"confirmPassword","𠮷".repeat(128)),token).statusCode()).isEqualTo(204);
        assertThat(request("POST","/api/v1/admin/sessions",Map.of("loginName",account,"password","𠮷".repeat(128)),null).statusCode()).isEqualTo(201);
    }
    private record PermissionAdmin(String account,String employee,String appointment) { }
    private PermissionAdmin permissionAdmin() {
        String account=account(false),employee=UUID.randomUUID().toString(),company=UUID.randomUUID().toString(),
                department=UUID.randomUUID().toString(),position=UUID.randomUUID().toString(),scope=UUID.randomUUID().toString(),appointment=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO employee(id,employee_key,name,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",employee,employee,"测试权限管理员");
        jdbc.update("INSERT INTO company(id,code,name,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",company,company,"测试公司");
        jdbc.update("INSERT INTO department(id,company_id,code,name,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6))",department,company,department,"测试部门");
        jdbc.update("INSERT INTO `position`(id,department_id,code,name,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(6))",position,department,position,"测试岗位");
        jdbc.update("INSERT INTO employment(id,employee_id,company_id,department_id,position_id,created_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))",
                UUID.randomUUID().toString(),employee,company,department,position);
        jdbc.update("UPDATE admin_account SET is_super_admin=0,employee_id=? WHERE id=?",employee,account);
        jdbc.update("INSERT INTO management_scope(id,company_id,department_id,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",scope,company,department);
        jdbc.update("INSERT INTO admin_appointment(id,scope_id,account_id,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",appointment,scope,account);
        return new PermissionAdmin(account,employee,appointment);
    }
    @Test void validAppointmentWithEmptySystemSetCanLoginAndRevocationIsImmediate() throws Exception {
        var p=permissionAdmin(); var response=login(p.account());assertThat(response.statusCode()).isEqualTo(201);
        assertThat(body(response).path("identity").stringValue()).isEqualTo("PERMISSION_ADMIN");
        String token=body(response).path("accessToken").stringValue();
        jdbc.update("UPDATE admin_appointment SET status='REVOKED',ended_at=UTC_TIMESTAMP(6),ended_reason='fixture',appointment_version=appointment_version+1 WHERE id=?",p.appointment());
        assertThat(request("GET","/api/v1/admin/sessions/current",null,token).statusCode()).isEqualTo(401);
        assertThat(login(p.account()).statusCode()).isEqualTo(401);
    }
    @Test void inactiveEmployeeCannotUseExistingSessionEvenWithActiveAppointment() throws Exception {
        var p=permissionAdmin();String token=token(p.account());
        jdbc.update("UPDATE employee SET status='INACTIVE',version=version+1 WHERE id=?",p.employee());
        assertThat(request("GET","/api/v1/admin/sessions/current",null,token).statusCode()).isEqualTo(401);
        assertThat(login(p.account()).statusCode()).isEqualTo(401);
    }
    @Test void credentialsChangedWhileWaitingForGuardCannotCreateSession() throws Exception {
        String account=account(false);
        try(var connection=database.runtimeConnection()) {
            connection.setAutoCommit(false);
            try(var statement=connection.createStatement();var rows=statement.executeQuery("SELECT revision FROM mutation_guard WHERE id=1 FOR UPDATE")){assertThat(rows.next()).isTrue();}
            var pending=java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return login(account);}catch(Exception e){throw new java.util.concurrent.CompletionException(e);}});
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(4);
            boolean waiting=false;
            while(System.nanoTime()<deadline) {
                if(!database.scalar("SELECT COUNT(*) FROM performance_schema.data_lock_waits").equals("0")){waiting=true;break;}
                Thread.sleep(20);
            }
            assertThat(waiting).as("real second connection waits for mutation_guard").isTrue();
            try(var statement=connection.prepareStatement("UPDATE admin_account SET credential_version=credential_version+1 WHERE id=?")){
                statement.setString(1,account);statement.executeUpdate();
            }
            connection.commit();
            assertThat(pending.get(10,java.util.concurrent.TimeUnit.SECONDS).statusCode()).isEqualTo(401);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_session WHERE account_id=?",Integer.class,account)).isZero();
    }
    private String serviceToken(String subject,String binding,boolean oa) throws Exception {
        var now=java.time.Instant.now();
        var claims=new com.nimbusds.jwt.JWTClaimsSet.Builder().issuer(oa?"urn:rbac:oa":"urn:rbac:business")
                .audience(oa?"rbac-oa-callback":"rbac-business").subject(subject).jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(300)))
                .claim(oa?"oaInstanceId":"systemId",binding).build();
        var key=oa?oaSigningKey:businessSigningKey;
        var signed=new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),claims);
        signed.sign(new com.nimbusds.jose.crypto.RSASSASigner(key));return signed.serialize();
    }
    @Test void serviceIdentityAndSystemBindingAreReadFromCurrentDatabase() throws Exception {
        String system=UUID.randomUUID().toString(),service=UUID.randomUUID().toString(),subject=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO business_system(id,code,name,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",system,system,"测试系统");
        jdbc.update("INSERT INTO service_identity(id,subject,kind,system_id) VALUES(?,?,'BUSINESS',?)",service,subject,system);
        String token=serviceToken(subject,system,false);
        // A valid identity reaches the normal 404 handler; this is not a production business endpoint.
        assertThat(request("GET","/api/v1/integration/identity-probe",null,token).statusCode()).isEqualTo(404);
        assertThat(request("GET","/api/v1/admin/sessions/current",null,token).statusCode()).isEqualTo(401);
        assertThat(request("GET","/api/v1/integration/identity-probe",null,serviceToken(subject,UUID.randomUUID().toString(),false)).statusCode()).isEqualTo(401);
        jdbc.update("UPDATE service_identity SET enabled=0 WHERE id=?",service);
        assertThat(request("GET","/api/v1/integration/identity-probe",null,token).statusCode()).isEqualTo(401);
    }
    @Test void oaServiceIdentityIsBoundToItsOwnInstanceAndCannotBeUsedAsBusinessToken() throws Exception {
        String service=UUID.randomUUID().toString(),subject=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO service_identity(id,subject,kind) VALUES(?,?,'OA')",service,subject);
        String token=serviceToken(subject,service,true);
        assertThat(request("POST","/api/v1/oa/identity-probe",Map.of(),token).statusCode()).isEqualTo(404);
        assertThat(request("GET","/api/v1/integration/identity-probe",null,token).statusCode()).isEqualTo(401);
        assertThat(request("POST","/api/v1/oa/identity-probe",Map.of(),serviceToken(subject,UUID.randomUUID().toString(),true)).statusCode()).isEqualTo(401);
        jdbc.update("UPDATE service_identity SET enabled=0 WHERE id=?",service);
        assertThat(request("POST","/api/v1/oa/identity-probe",Map.of(),token).statusCode()).isEqualTo(401);
    }
    @Test void httpLoginRateLimitReturnsProblemAndPositiveRetryAfter() throws Exception {
        String absent=UUID.randomUUID().toString();
        for(int n=0;n<10;n++) assertThat(login(absent).statusCode()).isEqualTo(401);
        var limited=login(absent);
        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(Integer.parseInt(limited.headers().firstValue("Retry-After").orElse("0"))).isPositive();
        assertThat(body(limited).path("code").stringValue()).isEqualTo("RATE_LIMITED");
        assertThat(limited.headers().firstValue("Content-Type")).hasValueSatisfying(v->assertThat(v).startsWith("application/problem+json"));
        assertThat(limited.headers().firstValue("Cache-Control")).contains("no-store");
    }
    @Test void unknownLoginFieldsAndMissingBearerFailWithoutExposingCredentials() throws Exception {
        String account=account(false);
        assertThat(request("POST","/api/v1/admin/sessions",Map.of("loginName",account,"password",password,"isSuperAdmin",true),null).statusCode()).isEqualTo(422);
        var denied=request("GET","/api/v1/admin/sessions/current",null,null);
        assertThat(denied.statusCode()).isEqualTo(401);
        assertThat(request("GET","/api/v1/%61dmin/sessions/current",null,null).statusCode()).isEqualTo(401);
        assertThat(denied.headers().firstValue("WWW-Authenticate")).contains("Bearer");
        assertThat(denied.headers().firstValue("X-Request-Id")).hasValueSatisfying(v->assertThat(v).matches("[A-Za-z0-9_-]{1,64}"));
        assertThat(denied.body()).doesNotContain(password,passwordHash);
    }
}
