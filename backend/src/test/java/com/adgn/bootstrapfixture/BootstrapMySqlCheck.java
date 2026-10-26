package com.adgn.bootstrapfixture;

import com.adgn.backend.testsupport.IsolatedMySqlDatabase;
import com.adgn.backend.identity.security.PasswordPolicy;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

class BootstrapMySqlCheck {
    private static final String PASSWORD="Fixture-initial-123456";

    private ProcessBuilder command(IsolatedMySqlDatabase database) {
        var process=com.adgn.backend.testsupport.JavaSubprocess.command(
                "-Dloader.main=com.adgn.backend.bootstrap.BootstrapAdminApplication", "-cp",
                "target/backend-0.0.1-SNAPSHOT.jar", "org.springframework.boot.loader.launch.PropertiesLauncher");
        process.environment().keySet().removeIf(k->k.startsWith("RBAC_")||k.startsWith("SPRING_")||k.equals("JAVA_TOOL_OPTIONS")||k.equals("JDK_JAVA_OPTIONS"));
        database.configureBootstrap(process);
        process.environment().put("RBAC_BOOTSTRAP_ADMIN_LOGIN","initial.admin");
        process.environment().put("RBAC_BOOTSTRAP_ADMIN_PASSWORD",PASSWORD);
        process.environment().put("RBAC_BOOTSTRAP_ADMIN_CONFIRM_PASSWORD",PASSWORD);
        process.redirectErrorStream(true);
        return process;
    }

    private record Result(int exit,String output) { }
    private Result run(ProcessBuilder command) throws Exception {
        var log=Files.createTempFile(Path.of(".tmp"),"bootstrap-output-",".log");
        var process=command.redirectOutput(log.toFile()).start();
        try {
            assertThat(process.waitFor(35,TimeUnit.SECONDS)).as("bootstrap exits without serving HTTP").isTrue();
            return new Result(process.exitValue(),Files.readString(log));
        } finally { if(process.isAlive())process.destroyForcibly().waitFor(); Files.deleteIfExists(log); }
    }

    @Test void packagedCommandCreatesHashedRestrictedSuperAdminAndDeploymentAuditAtomically() throws Exception {
        try(var database=new IsolatedMySqlDatabase()) {
            var result=run(command(database));
            assertThat(result.exit()).as("packaged administrator initialization succeeds").isZero();
            assertThat(database.scalar("SELECT COUNT(*) FROM admin_account WHERE employee_id IS NULL AND is_super_admin=1 AND enabled=1 AND must_change_password=1 AND version=1 AND credential_version=1 AND session_epoch=1")).isEqualTo("1");
            assertThat(database.scalar("SELECT COUNT(*) FROM admin_session")).isEqualTo("0");
            assertThat(database.scalar("SELECT COUNT(*) FROM audit_event WHERE actor_kind='DEPLOYMENT' AND event_type='ADMIN_BOOTSTRAPPED' AND visibility='GLOBAL'")).isEqualTo("1");
            assertThat(database.scalar("SELECT revision FROM mutation_guard WHERE id=1")).isEqualTo("1");
            var hash=database.scalar("SELECT password_hash FROM admin_account");
            assertThat(new PasswordPolicy().matches(PASSWORD,hash)).isTrue();
            assertThat(result.output()).doesNotContain(PASSWORD,hash,"jdbc:mysql:");
            assertThat(database.scalar("SELECT event_snapshot FROM audit_event")).doesNotContain(PASSWORD,hash);
        }
    }

    @Test void repeatedInvocationCannotResetEvenADisabledSuperAdmin() throws Exception {
        try(var database=new IsolatedMySqlDatabase()) {
            assertThat(run(command(database)).exit()).isZero();
            var hash=database.scalar("SELECT password_hash FROM admin_account");
            database.execute("UPDATE admin_account SET enabled=0");
            var retry=command(database);
            retry.environment().put("RBAC_BOOTSTRAP_ADMIN_PASSWORD","Different-fixture-123456");
            retry.environment().put("RBAC_BOOTSTRAP_ADMIN_CONFIRM_PASSWORD","Different-fixture-123456");
            var result=run(retry);
            assertThat(result.exit()).isEqualTo(3);
            assertThat(result.output()).contains("ADMIN_ALREADY_EXISTS").doesNotContain(PASSWORD,hash,"Different-fixture");
            assertThat(database.scalar("SELECT password_hash FROM admin_account")).isEqualTo(hash);
            assertThat(database.scalar("SELECT enabled FROM admin_account")).isEqualTo("0");
            assertThat(database.scalar("SELECT revision FROM mutation_guard WHERE id=1")).isEqualTo("1");
            assertThat(database.scalar("SELECT COUNT(*) FROM audit_event")).isEqualTo("1");
        }
    }

    @Test void concurrentProcessesCreateExactlyOneAdministratorAndOneAudit() throws Exception {
        try(var database=new IsolatedMySqlDatabase();var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var gate=new java.util.concurrent.CountDownLatch(1);
            var first=executor.submit(()->{gate.await();return run(command(database));});
            var second=executor.submit(()->{gate.await();return run(command(database));});
            gate.countDown();
            assertThat(List.of(first.get(50,TimeUnit.SECONDS).exit(),second.get(50,TimeUnit.SECONDS).exit())).containsExactlyInAnyOrder(0,3);
            assertThat(database.scalar("SELECT COUNT(*) FROM admin_account")).isEqualTo("1");
            assertThat(database.scalar("SELECT COUNT(*) FROM audit_event")).isEqualTo("1");
            assertThat(database.scalar("SELECT revision FROM mutation_guard WHERE id=1")).isEqualTo("1");
        }
    }

    @Test void auditFailureRollsBackAccountAndGuardAndAllowsAFreshAttempt() throws Exception {
        try(var database=new IsolatedMySqlDatabase()) {
            database.execute("CREATE TRIGGER fixture_bootstrap_audit BEFORE INSERT ON audit_event FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture audit failure'");
            var result=run(command(database));
            assertThat(result.exit()).isEqualTo(4);
            assertThat(result.output()).contains("DATABASE_OR_SCHEMA_FAILURE").doesNotContain("fixture audit failure",PASSWORD,"INSERT", "Exception");
            assertThat(database.scalar("SELECT COUNT(*) FROM admin_account")).isEqualTo("0");
            assertThat(database.scalar("SELECT COUNT(*) FROM audit_event")).isEqualTo("0");
            assertThat(database.scalar("SELECT revision FROM mutation_guard WHERE id=1")).isEqualTo("0");
            database.execute("DROP TRIGGER fixture_bootstrap_audit");
            assertThat(run(command(database)).exit()).isZero();
        }
    }

    @Test void invalidOrMismatchedConfigurationNeverWritesOrFallsBackToRuntimeCredentials() throws Exception {
        try(var database=new IsolatedMySqlDatabase()) {
            var mutations=List.<java.util.function.Consumer<ProcessBuilder>>of(
                    p->p.environment().remove("RBAC_BOOTSTRAP_URL"),
                    p->p.environment().put("RBAC_BOOTSTRAP_CONFIRM_DATABASE","another_database"),
                    p->p.environment().put("RBAC_BOOTSTRAP_ADMIN_PASSWORD","short"),
                    p->p.environment().put("RBAC_BOOTSTRAP_ADMIN_CONFIRM_PASSWORD","Mismatch-123456"),
                    p->p.environment().put("RBAC_BOOTSTRAP_ADMIN_LOGIN","bad login"),
                    p->p.environment().put("RBAC_BOOTSTRAP_URL",p.environment().get("RBAC_BOOTSTRAP_URL")+"?user=root"),
                    p->p.command().add("--password=Forbidden-fixture-123456"));
            for(var mutation:mutations) {
                var process=command(database);mutation.accept(process);
                var result=run(process);
                assertThat(result.exit()).isEqualTo(2);
                assertThat(result.output()).contains("INVALID_CONFIGURATION").doesNotContain(PASSWORD,"Forbidden-fixture","jdbc:mysql:","Exception");
            }
            assertThat(database.scalar("SELECT COUNT(*) FROM admin_account")).isEqualTo("0");
            assertThat(database.scalar("SELECT revision FROM mutation_guard WHERE id=1")).isEqualTo("0");
        }
    }

    @Test void missingMigrationOrGuardCannotBeSilentlyCreatedByBootstrap() throws Exception {
        try(var database=new IsolatedMySqlDatabase()) {
            database.execute("DELETE FROM mutation_guard");
            assertThat(run(command(database)).exit()).isEqualTo(4);
            assertThat(database.scalar("SELECT COUNT(*) FROM admin_account")).isEqualTo("0");
            assertThat(database.scalar("SELECT COUNT(*) FROM mutation_guard")).isEqualTo("0");
            database.execute("DELETE FROM flyway_schema_history WHERE version='3'");
            var result=run(command(database));
            assertThat(result.exit()).isEqualTo(4);
            assertThat(database.scalar("SELECT COUNT(*) FROM flyway_schema_history WHERE version='3'")).isEqualTo("0");
        }
    }

    @Test void bootstrapRejectsSystemCatalogCredentialsAndAmbiguousUrlBeforeWriting() throws Exception {
        try(var database=new IsolatedMySqlDatabase()) {
            var mutations=List.<java.util.function.Consumer<ProcessBuilder>>of(
                    p->p.environment().put("RBAC_BOOTSTRAP_ADMIN_LOGIN","_invalid"),
                    p->p.environment().put("RBAC_BOOTSTRAP_ADMIN_LOGIN","a".repeat(101)),
                    p->p.environment().put("RBAC_BOOTSTRAP_USER"," "),
                    p->p.environment().put("RBAC_BOOTSTRAP_CONFIRM_DATABASE","mysql"),
                    p->p.environment().put("RBAC_BOOTSTRAP_CONFIRM_DATABASE","bad-name"),
                    p->p.environment().put("RBAC_BOOTSTRAP_URL","jdbc:postgresql://127.0.0.1/db"),
                    p->p.environment().put("RBAC_BOOTSTRAP_URL",p.environment().get("RBAC_BOOTSTRAP_URL").replace("127.0.0.1","embedded:private-fixture@127.0.0.1")),
                    p->p.environment().put("RBAC_BOOTSTRAP_URL",p.environment().get("RBAC_BOOTSTRAP_URL")+"#fragment"),
                    p->p.environment().put("RBAC_BOOTSTRAP_URL","jdbc:mysql://127.0.0.1:0/"+p.environment().get("RBAC_BOOTSTRAP_CONFIRM_DATABASE")),
                    p->p.environment().put("RBAC_BOOTSTRAP_URL","jdbc:mysql://127.0.0.1:65536/"+p.environment().get("RBAC_BOOTSTRAP_CONFIRM_DATABASE")));
            for(var mutation:mutations){
                var process=command(database);mutation.accept(process);var result=run(process);
                assertThat(result.exit()).isEqualTo(2);
                assertThat(result.output()).contains("INVALID_CONFIGURATION").doesNotContain(PASSWORD,"private-fixture","jdbc:");
            }
            assertThat(database.scalar("SELECT COUNT(*) FROM admin_account")).isEqualTo("0");
            assertThat(database.scalar("SELECT COUNT(*) FROM audit_event")).isEqualTo("0");
            assertThat(database.scalar("SELECT revision FROM mutation_guard WHERE id=1")).isEqualTo("0");
        }
    }

    @Test void occupiedNonSuperLoginIsNeverPromoted() throws Exception {
        try(var database=new IsolatedMySqlDatabase()) {
            var employee=UUID.randomUUID().toString();
            database.execute("INSERT INTO employee(id,employee_key,name,created_at) VALUES('"+employee+"','fixture','Fixture',UTC_TIMESTAMP(6))");
            database.execute("INSERT INTO admin_account(id,employee_id,login_name,password_hash,created_at) VALUES(UUID(),'"+employee+"','initial.admin','unusable-fixture',UTC_TIMESTAMP(6))");
            var result=run(command(database));
            assertThat(result.exit()).isEqualTo(3);
            assertThat(result.output()).contains("LOGIN_ALREADY_EXISTS");
            assertThat(database.scalar("SELECT is_super_admin FROM admin_account")).isEqualTo("0");
            assertThat(database.scalar("SELECT COUNT(*) FROM audit_event")).isEqualTo("0");
        }
    }

    @Test void ordinaryStartupDoesNotInitializeAndBootstrappedAccountMustChangePasswordBeforeAdministration() throws Exception {
        var keys=Files.createTempDirectory(Path.of(".tmp"),"be32-keys-");
        try(var database=new IsolatedMySqlDatabase()) {
            for(var family:List.of("admin","business","oa")) {
                var key=new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).keyID(family).generate();
                Files.writeString(keys.resolve(family+".json"),new com.nimbusds.jose.jwk.JWKSet(family.equals("admin")?key:key.toPublicJWK()).toString(!family.equals("admin")));
            }
            try(var application=database.startWeb("--rbac.identity.enabled=true","--rbac.identity.admin-key-id=admin",
                    "--rbac.identity.admin-jwk-set="+keys.resolve("admin.json").toAbsolutePath(),
                    "--rbac.identity.business-jwk-set="+keys.resolve("business.json").toAbsolutePath(),
                    "--rbac.identity.oa-jwk-set="+keys.resolve("oa.json").toAbsolutePath())) {
                assertThat(database.scalar("SELECT COUNT(*) FROM admin_account")).isEqualTo("0");
                assertThat(run(command(database)).exit()).isZero();
                String base="http://127.0.0.1:"+((org.springframework.boot.web.server.context.WebServerApplicationContext)application).getWebServer().getPort();
                var json=tools.jackson.databind.json.JsonMapper.builder().build();
                var login=request(base,"POST","/api/v1/admin/sessions",json.writeValueAsString(Map.of("loginName","initial.admin","password",PASSWORD)),null);
                assertThat(login.statusCode()).isEqualTo(201);
                var restricted=json.readTree(login.body()).path("accessToken").stringValue();
                assertThat(request(base,"GET","/api/v1/admin/accounts",null,restricted).statusCode()).isEqualTo(403);
                var change=request(base,"PUT","/api/v1/admin/password",json.writeValueAsString(Map.of("currentPassword",PASSWORD,"newPassword","Updated-fixture-123456","confirmPassword","Updated-fixture-123456")),restricted);
                assertThat(change.statusCode()).isEqualTo(204);
                assertThat(request(base,"GET","/api/v1/admin/accounts",null,restricted).statusCode()).isEqualTo(401);
                var normalLogin=request(base,"POST","/api/v1/admin/sessions",json.writeValueAsString(Map.of("loginName","initial.admin","password","Updated-fixture-123456")),null);
                assertThat(normalLogin.statusCode()).isEqualTo(201);
                var normal=json.readTree(normalLogin.body()).path("accessToken").stringValue();
                assertThat(request(base,"GET","/api/v1/admin/accounts",null,normal).statusCode()).isEqualTo(200);
                assertThat(database.scalar("SELECT must_change_password FROM admin_account")).isEqualTo("0");
            }
        } finally {
            for(var family:List.of("admin","business","oa"))Files.deleteIfExists(keys.resolve(family+".json"));
            Files.deleteIfExists(keys);
        }
    }

    private java.net.http.HttpResponse<String> request(String base,String method,String path,String body,String token) throws Exception {
        var builder=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+path)).timeout(java.time.Duration.ofSeconds(25));
        if(body!=null)builder.header("Content-Type","application/json");
        if(token!=null)builder.header("Authorization","Bearer "+token);
        return com.adgn.backend.testsupport.ContractHttpClient.create().send(builder.method(method,body==null?java.net.http.HttpRequest.BodyPublishers.noBody():java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
    }
}
