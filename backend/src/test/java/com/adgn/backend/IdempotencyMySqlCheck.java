package com.adgn.backend;

import com.adgn.backend.core.idempotency.*;
import com.adgn.backend.core.transaction.*;
import com.adgn.backend.core.audit.*;
import com.adgn.backend.testsupport.IsolatedMySqlDatabase;
import org.junit.jupiter.api.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdempotencyMySqlCheck {
    @Test void reservationRejectsMissingServiceOrDifferentPrincipalBeforeWriting() throws Exception {
        var request=request();long revision=jdbc.queryForObject("SELECT revision FROM mutation_guard WHERE id=1",Long.class);
        java.util.List<CommandBoundary.CheckedSupplier<CommandBoundary.Identity<String>>> actors=Arrays.asList(
                ()->null,()->new CommandBoundary.Identity<>("current",new AuditRecord.Actor(AuditRecord.ActorKind.SERVICE,principal.toString())),
                ()->new CommandBoundary.Identity<>("current",new AuditRecord.Actor(AuditRecord.ActorKind.ACCOUNT,UUID.randomUUID().toString())));
        for(var actor:actors)assertCode(()->coordinator.reserve(request,actor,this::authorize),IdempotencyFailure.Code.AUTHENTICATION_FAILED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM http_idempotency WHERE idempotency_key=?",Long.class,request.key().toString())).isZero();
        assertThat(jdbc.queryForObject("SELECT revision FROM mutation_guard WHERE id=1",Long.class)).isEqualTo(revision);
    }

    @Test void corruptedCachedProjectionCannotReplaySensitiveFieldsOrRunBusinessAgain() throws Exception {
        var request=request();
        // 插入受损持久化夹具；已完成结果的不可变触发器保持启用。
        jdbc.update("INSERT INTO http_idempotency(id,principal_id,http_method,canonical_path,idempotency_key,request_digest,digest_key_id,state,http_status,safe_response,authorization_basis,expires_at,created_at) VALUES(?,?,?,?,?,?,?,'COMPLETED',201,?,?,UTC_TIMESTAMP(6)+INTERVAL 7 DAY,UTC_TIMESTAMP(6))",
                UUID.randomUUID().toString(),principal.toString(),request.method(),request.path(),request.key().toString(),keys.digest("old",request),"old",
                "{\"schemaVersion\":1,\"body\":{\"password\":\"private-fixture\"}}",json.writeValueAsString(basis()));
        assertThatThrownBy(()->coordinator.execute(request,this::qualify,this::authorize,c->{throw new AssertionError("must not rerun");}))
                .isInstanceOfSatisfying(PersistenceFailure.class,e->assertThat(e.kind()).isEqualTo(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR));
        assertThat(state(request)).isEqualTo("COMPLETED");
    }
    private IsolatedMySqlDatabase database;
    private ConfigurableApplicationContext application;
    private JdbcTemplate jdbc;
    private CommandBoundary commands;
    private IdempotencyCoordinator coordinator;
    private DataSource source;
    private UUID principal;
    private HmacKeyRing keys;
    private JsonMapper json;
    @BeforeAll void start() throws Exception {
        database=new IsolatedMySqlDatabase(); application=database.start(BackendApplication.class);
        source=application.getBean(DataSource.class); jdbc=new JdbcTemplate(source);
        commands=application.getBean(CommandBoundary.class); json=application.getBean(JsonMapper.class);
        byte[] key=new byte[32]; new java.security.SecureRandom().nextBytes(key); keys=new HmacKeyRing("old",Map.of("old",key));
        coordinator=new IdempotencyCoordinator(commands,source,keys,json);
        principal=UUID.randomUUID();
        jdbc.update("INSERT INTO admin_account(id,login_name,password_hash,is_super_admin,created_at) VALUES(?,?,?,1,UTC_TIMESTAMP(6))",principal.toString(),principal.toString(),"fixture-not-a-password");
    }
    @AfterAll void close() throws Exception { if(application!=null)application.close(); if(database!=null)database.close(); }
    private IdempotencyRequest request() { return request(UUID.randomUUID(),"{\"expectedVersion\":1}"); }
    private IdempotencyRequest request(UUID key,String body) { return new IdempotencyRequest(principal,"fixtureWrite","POST","/api/v1/fixture",key,body.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    private CommandBoundary.Identity<String> qualify() {
        if(jdbc.queryForObject("SELECT enabled FROM admin_account WHERE id=?",Integer.class,principal.toString())!=1)
            throw new IdempotencyFailure(IdempotencyFailure.Code.AUTHENTICATION_FAILED,0);
        return new CommandBoundary.Identity<>("current",new AuditRecord.Actor(AuditRecord.ActorKind.ACCOUNT,principal.toString()));
    }
    private void authorize(String current,AuthorizationBasis basis) { assertThat(current).isEqualTo("current"); }
    private AuthorizationBasis basis() { return new AuthorizationBasis("fixtureWrite",AuthorizationBasis.Policy.GLOBAL_ONLY,List.of()); }
    private IdempotencyCoordinator.Completion success(String body) { return new IdempotencyCoordinator.Completion(new SafeResult(201,body,"/api/v1/fixture/result"),basis()); }
    private String state(IdempotencyRequest r) { return jdbc.queryForObject("SELECT state FROM http_idempotency WHERE principal_id=? AND idempotency_key=?",String.class,principal.toString(),r.key().toString()); }
    private void expire(IdempotencyRequest r) { jdbc.update("UPDATE http_idempotency SET lease_until=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE idempotency_key=?",r.key().toString()); }
    private void insertCompany(UUID id) { jdbc.update("INSERT INTO company(id,code,name,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))",id.toString(),id.toString(),"fixture"); }
    private long countCompany(UUID id) { return jdbc.queryForObject("SELECT COUNT(*) FROM company WHERE id=?",Long.class,id.toString()); }
    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable work,IdempotencyFailure.Code code) {
        assertThatThrownBy(work).isInstanceOfSatisfying(IdempotencyFailure.class,e->assertThat(e.code()).isEqualTo(code));
    }
    @Test void successfulCommitThenLostResponseReplaysWithoutCallingBusinessOrVersionCheck() throws Exception {
        var r=request(); UUID target=UUID.randomUUID();
        var first=coordinator.execute(r,this::qualify,this::authorize,c->{insertCompany(target);return success("{\"version\":2}");});
        assertThat(first.replayed()).isFalse(); assertThat(state(r)).isEqualTo("COMPLETED");
        var again=coordinator.execute(r,this::qualify,this::authorize,c->{throw new AssertionError("must not compare old version or rerun");});
        assertThat(again.replayed()).isTrue(); assertThat(again.result()).isEqualTo(first.result()); assertThat(countCompany(target)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT expires_at>=UTC_TIMESTAMP(6)+INTERVAL 6 DAY FROM http_idempotency WHERE idempotency_key=?",Boolean.class,r.key().toString())).isTrue();
    }
    @Test void reservationSurvivesCheckedRollbackAndStillRejectsChangedContent() throws Exception {
        var r=request(); UUID target=UUID.randomUUID();
        var ticket=(IdempotencyCoordinator.Ticket)coordinator.reserve(r,this::qualify,this::authorize);
        assertThatThrownBy(()->coordinator.complete(ticket,this::qualify,this::authorize,c->{insertCompany(target);throw new Exception("checked fixture");})).hasMessage("checked fixture");
        assertThat(countCompany(target)).isZero(); assertThat(state(r)).isEqualTo("IN_PROGRESS");
        assertCode(()->coordinator.reserve(request(r.key(),"changed"),this::qualify,this::authorize),IdempotencyFailure.Code.IDEMPOTENCY_CONFLICT);
        assertThatThrownBy(()->coordinator.reserve(r,this::qualify,this::authorize)).isInstanceOfSatisfying(IdempotencyFailure.class,e->{assertThat(e.code()).isEqualTo(IdempotencyFailure.Code.REQUEST_IN_PROGRESS);assertThat(e.retryAfter()).isPositive();});
    }
    @Test void expiredReservationCanBeTakenOverButOldWorkerCannotCommit() throws Exception {
        var r=request(); var old=(IdempotencyCoordinator.Ticket)coordinator.reserve(r,this::qualify,this::authorize);
        expire(r); var newer=(IdempotencyCoordinator.Ticket)coordinator.reserve(r,this::qualify,this::authorize);
        assertThat(newer.token()).isNotEqualTo(old.token());
        assertCode(()->coordinator.complete(old,this::qualify,this::authorize,c->{throw new AssertionError("stale work");}),IdempotencyFailure.Code.REQUEST_IN_PROGRESS);
        var done=coordinator.complete(newer,this::qualify,this::authorize,c->success("{}")); assertThat(done.replayed()).isFalse();
        assertCode(()->coordinator.complete(old,this::qualify,this::authorize,c->success("{}")),IdempotencyFailure.Code.REQUEST_IN_PROGRESS);
    }
    @Test void deletedTarget204ReplayUsesSavedScopeAndRejectsRevokedCurrentCoverage() throws Exception {
        var r=request();UUID target=UUID.randomUUID();insertCompany(target);
        var b=new AuthorizationBasis("fixtureWrite",AuthorizationBasis.Policy.SCOPED,List.of(new AuthorizationBasis.Target(target,UUID.randomUUID(),UUID.randomUUID())));
        coordinator.execute(r,this::qualify,this::authorize,c->{jdbc.update("DELETE FROM company WHERE id=?",target.toString());return new IdempotencyCoordinator.Completion(new SafeResult(204,null,null),b);});
        var replay=coordinator.execute(r,this::qualify,(a,saved)->assertThat(saved).isEqualTo(b),c->{throw new AssertionError();});
        assertThat(replay.result().status()).isEqualTo(204); assertThat(replay.result().body()).isNull(); assertThat(countCompany(target)).isZero();
        assertCode(()->coordinator.reserve(r,this::qualify,(a,saved)->{throw new IdempotencyFailure(IdempotencyFailure.Code.FORBIDDEN,0);}),IdempotencyFailure.Code.FORBIDDEN);
    }
    @Test void revokedAccountCannotReplayAnyCachedResponse() throws Exception {
        var r=request();coordinator.execute(r,this::qualify,this::authorize,c->success("{}"));
        jdbc.update("UPDATE admin_account SET enabled=0 WHERE id=?",principal.toString());
        try { assertCode(()->coordinator.reserve(r,this::qualify,this::authorize),IdempotencyFailure.Code.AUTHENTICATION_FAILED); }
        finally {jdbc.update("UPDATE admin_account SET enabled=1 WHERE id=?",principal.toString());}
    }
    @Test void cachedResultFailureRollsBackBusinessAndAuditAndKeepsReservation() throws Exception {
        var r=request(); UUID target=UUID.randomUUID();String correlation=UUID.randomUUID().toString();
        var appender=application.getBean(AuditAppender.class);
        assertThatThrownBy(()->coordinator.execute(r,this::qualify,this::authorize,c->{
            insertCompany(target);
            appender.append(c,new AuditRecord("FIXTURE",target.toString(),null,null,new AuditRecord.Scope(AuditRecord.Visibility.GLOBAL,null,null,null),"FIXTURE",correlation,"fixture","{\"schemaVersion\":1}",List.of()));
            // Completion validation rejects missing authorization facts after earlier writes.
            return new IdempotencyCoordinator.Completion(new SafeResult(201,"{}",null),null);
        })).isInstanceOf(RuntimeException.class);
        assertThat(countCompany(target)).isZero();assertThat(state(r)).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE correlation_id=?",Long.class,correlation)).isZero();
    }
    @Test void explicitSafeFailureIsCachedOnlyAfterOriginalBusinessRollback() throws Exception {
        var r=request();UUID target=UUID.randomUUID();
        var b=new AuthorizationBasis("fixtureWrite",AuthorizationBasis.Policy.PRINCIPAL_ONLY,List.of());
        var failure=coordinator.execute(r,this::qualify,this::authorize,c->{insertCompany(target);throw new IdempotencyCoordinator.SafeFailure("VERSION_CONFLICT",b);});
        assertThat(failure.result().status()).isEqualTo(409);assertThat(countCompany(target)).isZero();assertThat(state(r)).isEqualTo("COMPLETED");
        var replay=(IdempotencyCoordinator.Outcome)coordinator.reserve(r,this::qualify,this::authorize);
        assertThat(replay.result().body()).contains("VERSION_CONFLICT");assertThat(replay.replayed()).isTrue();
    }
    @Test void temporaryDependencyFailureIsNeverPermanentlyCached() throws Exception {
        var r=request();
        assertThatThrownBy(()->coordinator.execute(r,this::qualify,this::authorize,c->{throw new PersistenceFailure(PersistenceFailure.Kind.DEPENDENCY_UNAVAILABLE,null);})).isInstanceOf(PersistenceFailure.class);
        assertThat(state(r)).isEqualTo("IN_PROGRESS");expire(r);
        var done=coordinator.execute(r,this::qualify,this::authorize,c->success("{}"));assertThat(done.result().status()).isEqualTo(201);
    }
    @Test void missingHistoricalKeyFailsClosedAndRetainedKeySupportsRotation() throws Exception {
        var r=request();coordinator.execute(r,this::qualify,this::authorize,c->success("{}"));
        byte[] next=new byte[32];new java.security.SecureRandom().nextBytes(next);
        var missing=new IdempotencyCoordinator(commands,source,new HmacKeyRing("next",Map.of("next",next)),json);
        assertThatThrownBy(()->missing.reserve(r,this::qualify,this::authorize)).isInstanceOf(PersistenceFailure.class);
        assertThat(state(r)).isEqualTo("COMPLETED");
    }
    @Test void simultaneousIdenticalRequestsCommitBusinessOnce() throws Exception {
        var r=request();UUID target=UUID.randomUUID();var calls=new AtomicInteger();
        var reserved=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->{var ticket=(IdempotencyCoordinator.Ticket)coordinator.reserve(r,this::qualify,this::authorize);reserved.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError();return coordinator.complete(ticket,this::qualify,this::authorize,c->{calls.incrementAndGet();insertCompany(target);return success("{}");});});
            assertThat(reserved.await(10,TimeUnit.SECONDS)).isTrue();
            var second=pool.submit(()->{assertCode(()->coordinator.execute(r,this::qualify,this::authorize,c->{calls.incrementAndGet();insertCompany(target);return success("{}");}),IdempotencyFailure.Code.REQUEST_IN_PROGRESS);return true;});
            assertThat(second.get(10,TimeUnit.SECONDS)).isTrue();release.countDown();first.get(10,TimeUnit.SECONDS);
            assertThat(calls.get()).isEqualTo(1);assertThat(countCompany(target)).isEqualTo(1);
        } finally {release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void normalBusinessCannotReturnAnArbitrary4xxAndCommitInsteadOfRollingBack() throws Exception {
        var r=request();UUID target=UUID.randomUUID();
        assertThatThrownBy(()->coordinator.execute(r,this::qualify,this::authorize,c->{insertCompany(target);return new IdempotencyCoordinator.Completion(new SafeResult(409,"{\"code\":\"STATE_CONFLICT\"}",null),basis());})).isInstanceOf(PersistenceFailure.class);
        assertThat(countCompany(target)).isZero();assertThat(state(r)).isEqualTo("IN_PROGRESS");
    }
    @Test void actualCompletionSqlFailureRollsBackAuditAndBusinessTogether() throws Exception {
        var r=request();UUID target=UUID.randomUUID();String correlation=UUID.randomUUID().toString();
        database.execute("CREATE TRIGGER fixture_completion_failure BEFORE UPDATE ON http_idempotency FOR EACH ROW BEGIN IF NEW.state='COMPLETED' AND NEW.idempotency_key='"+r.key()+"' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='fixture completion failure'; END IF; END");
        try {
            assertThatThrownBy(()->coordinator.execute(r,this::qualify,this::authorize,c->{
                insertCompany(target);application.getBean(AuditAppender.class).append(c,new AuditRecord("FIXTURE",target.toString(),null,null,new AuditRecord.Scope(AuditRecord.Visibility.GLOBAL,null,null,null),"FIXTURE",correlation,"fixture","{\"schemaVersion\":1}",List.of()));
                return success("{}");
            })).isInstanceOf(PersistenceFailure.class);
            assertThat(countCompany(target)).isZero();assertThat(state(r)).isEqualTo("IN_PROGRESS");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE correlation_id=?",Long.class,correlation)).isZero();
        } finally { database.execute("DROP TRIGGER fixture_completion_failure"); }
    }
    @Test void qualificationIsRecheckedAfterReservationAndExpiredTicketDoesNotRunBusiness() throws Exception {
        var r=request();var ticket=(IdempotencyCoordinator.Ticket)coordinator.reserve(r,this::qualify,this::authorize);
        jdbc.update("UPDATE admin_account SET enabled=0 WHERE id=?",principal.toString());
        try {assertCode(()->coordinator.complete(ticket,this::qualify,this::authorize,c->{throw new AssertionError();}),IdempotencyFailure.Code.AUTHENTICATION_FAILED);}
        finally {jdbc.update("UPDATE admin_account SET enabled=1 WHERE id=?",principal.toString());}
        expire(r);assertCode(()->coordinator.complete(ticket,this::qualify,this::authorize,c->{throw new AssertionError();}),IdempotencyFailure.Code.REQUEST_IN_PROGRESS);
    }
    @Test void sameKeyInDifferentMethodPathAndAccountDoesNotReplayAnotherOperation() throws Exception {
        var first=request();coordinator.execute(first,this::qualify,this::authorize,c->success("{}"));
        var otherPath=new IdempotencyRequest(principal,"fixtureWrite","POST","/api/v1/other",first.key(),first.canonicalBody());
        var otherMethod=new IdempotencyRequest(principal,"fixtureWrite","PUT",first.path(),first.key(),first.canonicalBody());
        assertThat(coordinator.execute(otherPath,this::qualify,this::authorize,c->success("{}")).replayed()).isFalse();
        assertThat(coordinator.execute(otherMethod,this::qualify,this::authorize,c->success("{}")).replayed()).isFalse();
        UUID another=UUID.randomUUID();jdbc.update("INSERT INTO admin_account(id,login_name,password_hash,is_super_admin,created_at) VALUES(?,?,?,1,UTC_TIMESTAMP(6))",another.toString(),another.toString(),"fixture-not-a-password");
        var otherAccount=new IdempotencyRequest(another,"fixtureWrite","POST",first.path(),first.key(),first.canonicalBody());
        assertThat(coordinator.execute(otherAccount,()->new CommandBoundary.Identity<>("current",new AuditRecord.Actor(AuditRecord.ActorKind.ACCOUNT,another.toString())),this::authorize,c->success("{}")).replayed()).isFalse();
    }
    @Test void retryWaitsForInFlightCommitThenReplaysInsteadOfExecutingAgain() throws Exception {
        var r=request();UUID target=UUID.randomUUID();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->coordinator.execute(r,this::qualify,this::authorize,c->{insertCompany(target);entered.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError();return success("{}");}));
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            var second=pool.submit(()->coordinator.execute(r,this::qualify,this::authorize,c->{throw new AssertionError("duplicate business");}));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);boolean waiting=false;
            while(System.nanoTime()<deadline){if(!database.scalar("SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l ON w.REQUESTING_ENGINE_LOCK_ID=l.ENGINE_LOCK_ID WHERE l.OBJECT_SCHEMA=DATABASE() AND l.OBJECT_NAME='mutation_guard'").equals("0")){waiting=true;break;}Thread.sleep(10);}
            assertThat(waiting).isTrue();assertThat(second.isDone()).isFalse();release.countDown();first.get(10,TimeUnit.SECONDS);
            assertThat(second.get(10,TimeUnit.SECONDS).replayed()).isTrue();assertThat(countCompany(target)).isEqualTo(1);
        }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void rejectionFromQualificationIsNotMistakenForACacheableBusinessFailure() throws Exception {
        var r=request();var ticket=(IdempotencyCoordinator.Ticket)coordinator.reserve(r,this::qualify,this::authorize);
        var checks=new AtomicInteger();
        var b=new AuthorizationBasis("fixtureWrite",AuthorizationBasis.Policy.PRINCIPAL_ONLY,List.of());
        assertThatThrownBy(()->coordinator.complete(ticket,()->{
            if(checks.incrementAndGet()==1)throw new IdempotencyCoordinator.SafeFailure("STATE_CONFLICT",b);
            return qualify();
        },this::authorize,c->{throw new AssertionError("unqualified business");})).isInstanceOf(IdempotencyCoordinator.SafeFailure.class);
        assertThat(state(r)).isEqualTo("IN_PROGRESS");
    }
    @Test void mismatchedDatasourceCannotEscapeTheGuardedTransaction() throws Exception {
        var r=request();
        var wrong=new IdempotencyCoordinator(commands,new org.springframework.jdbc.datasource.DelegatingDataSource(source),keys,json);
        assertThatThrownBy(()->wrong.reserve(r,this::qualify,this::authorize)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM http_idempotency WHERE idempotency_key=?",Long.class,r.key().toString())).isZero();
    }
}
