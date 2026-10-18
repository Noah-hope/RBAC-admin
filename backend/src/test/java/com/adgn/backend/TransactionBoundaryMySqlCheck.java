package com.adgn.backend;

import com.adgn.backend.core.audit.AuditAppender;
import com.adgn.backend.core.audit.AuditRecord;
import com.adgn.backend.core.transaction.CommandBoundary;
import com.adgn.backend.core.transaction.PersistenceFailure;
import com.adgn.backend.core.transaction.ReadBoundary;
import com.adgn.backend.core.transaction.WriteContext;
import com.adgn.backend.testsupport.IsolatedMySqlDatabase;
import com.adgn.persistencefixture.PersistenceSamples;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransactionBoundaryMySqlCheck {
    private IsolatedMySqlDatabase database;
    private ConfigurableApplicationContext application;
    private CommandBoundary commands;
    private ReadBoundary reads;
    private AuditAppender audits;
    private JdbcTemplate jdbc;
    private EntityManager entities;

    @BeforeAll
    void startIsolatedApplication() throws Exception {
        database = new IsolatedMySqlDatabase();
        application = database.start(BackendApplication.class, PersistenceSamples.MappingConfiguration.class, FixtureClock.class);
        var dataSource = application.getBean(DataSource.class);
        jdbc = new JdbcTemplate(dataSource);
        entities = SharedEntityManagerCreator.createSharedEntityManager(application.getBean(EntityManagerFactory.class));
        commands = application.getBean(CommandBoundary.class);
        reads = application.getBean(ReadBoundary.class);
        audits = application.getBean(AuditAppender.class);
    }

    @AfterAll
    void stopIsolatedApplication() throws Exception {
        if (application != null) application.close();
        if (database != null) database.close();
    }

    @Test
    void qualificationRunsInsideRcAndBusinessAuditSubjectsAndRevisionCommitTogether() throws Exception {
        UUID id = UUID.randomUUID();
        String correlation = UUID.randomUUID().toString();
        long before = revision();
        AtomicReference<Long> qualificationConnection = new AtomicReference<>();
        UUID auditId = commands.execute(() -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            assertThat(jdbc.queryForObject("SELECT @@transaction_isolation", String.class)).isEqualTo("READ-COMMITTED");
            qualificationConnection.set(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class));
            return identity("current-qualification");
        }, context -> {
            context.requireActive();
            assertThat(context.authority()).isEqualTo("current-qualification");
            assertThat(jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class)).isEqualTo(qualificationConnection.get());
            insertCompany(id, "Initial");
            return audits.append(context, event(id, correlation, List.of(new AuditRecord.Subject("COMPANY", id.toString()))));
        });
        assertThat(database.scalar("SELECT name FROM company WHERE id='" + id + "'")).isEqualTo("Initial");
        assertThat(database.scalar("SELECT actor_id FROM audit_event WHERE id='" + auditId + "'")).isEqualTo("fixture-service");
        assertThat(database.scalar("SELECT DATE_FORMAT(occurred_at,'%Y-%m-%d %H:%i:%s.%f') FROM audit_event WHERE id='" + auditId + "'"))
                .isEqualTo("2026-10-18 03:04:05.123456");
        assertThat(database.scalar("SELECT COUNT(*) FROM audit_subject WHERE audit_event_id='" + auditId + "'")).isEqualTo("1");
        assertThat(revision()).isEqualTo(before + 1);
    }

    @Test
    void checkedFailureRollsBackEveryBatchItemAuditAndRevision() throws Exception {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        String correlation = UUID.randomUUID().toString();
        long before = revision();
        var failure = new Exception("checked fixture failure");
        assertThatThrownBy(() -> commands.execute(() -> identity("allowed"), context -> {
            insertCompany(first, "First");
            audits.append(context, event(first, correlation, List.of()));
            insertCompany(second, "Second");
            throw failure;
        })).isSameAs(failure);
        assertThat(database.scalar("SELECT COUNT(*) FROM company WHERE id IN ('" + first + "','" + second + "')")).isEqualTo("0");
        assertThat(database.scalar("SELECT COUNT(*) FROM audit_event WHERE correlation_id='" + correlation + "'")).isEqualTo("0");
        assertThat(revision()).isEqualTo(before);
    }

    @Test
    void deferredJpaFlushFailureRollsBackJdbcBusinessAuditAndRevision() throws Exception {
        UUID existing = UUID.randomUUID(), duplicate = UUID.randomUUID(), company = UUID.randomUUID();
        String correlation = UUID.randomUUID().toString();
        database.execute("INSERT INTO business_system(id,code,name,created_at) VALUES('" + existing
                + "','FIXTURE_" + duplicate + "','Existing',UTC_TIMESTAMP(6))");
        long before = revision();
        assertThatThrownBy(() -> commands.execute(() -> identity("allowed"), context -> {
            insertCompany(company, "Must roll back");
            entities.persist(new PersistenceSamples.SystemSample(duplicate, Instant.parse("2026-10-18T03:00:00Z")));
            audits.append(context, event(company, correlation, List.of()));
            return "must not commit";
        })).isInstanceOf(PersistenceFailure.class);
        assertThat(database.scalar("SELECT COUNT(*) FROM company WHERE id='" + company + "'")).isEqualTo("0");
        assertThat(database.scalar("SELECT COUNT(*) FROM audit_event WHERE correlation_id='" + correlation + "'")).isEqualTo("0");
        assertThat(database.scalar("SELECT COUNT(*) FROM business_system WHERE id='" + duplicate + "'")).isEqualTo("0");
        assertThat(revision()).isEqualTo(before);
    }

    @Test
    void evenCaughtAuditSubjectFailureCannotCommitPartialBusinessOrAudit() throws Exception {
        UUID id = UUID.randomUUID();
        String correlation = UUID.randomUUID().toString();
        long before = revision();
        var duplicate = new AuditRecord.Subject("COMPANY", id.toString());
        assertThatThrownBy(() -> commands.execute(() -> identity("allowed"), context -> {
            insertCompany(id, "Must roll back");
            try { audits.append(context, event(id, correlation, List.of(duplicate, duplicate))); }
            catch (RuntimeException ignored) { /* outer boundary must still refuse success */ }
            return "must not succeed";
        })).isInstanceOf(PersistenceFailure.class);
        assertThat(database.scalar("SELECT COUNT(*) FROM company WHERE id='" + id + "'")).isEqualTo("0");
        assertThat(database.scalar("SELECT COUNT(*) FROM audit_event WHERE correlation_id='" + correlation + "'")).isEqualTo("0");
        assertThat(database.scalar("SELECT COUNT(*) FROM audit_subject WHERE subject_id='" + id + "'")).isEqualTo("0");
        assertThat(revision()).isEqualTo(before);
    }

    @Test
    void expiredAndCrossThreadWriteContextsAreRejected() throws Exception {
        AtomicReference<WriteContext<String>> saved = new AtomicReference<>();
        commands.execute(() -> identity("allowed"), context -> {
            saved.set(context);
            try (var executor = Executors.newSingleThreadExecutor()) {
                var failure = executor.submit(() -> catchThrowable(context::requireActive)).get(3, TimeUnit.SECONDS);
                assertThat(failure).isInstanceOf(IllegalStateException.class);
            }
            return null;
        });
        assertThatThrownBy(saved.get()::requireActive).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aContextCannotBeReusedInsideASuspendedIndependentTransaction() throws Exception {
        var independent = new TransactionTemplate(application.getBean(PlatformTransactionManager.class));
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        commands.execute(() -> identity("outer"), context -> {
            assertThatThrownBy(() -> independent.execute(status -> { context.requireActive(); return null; }))
                    .isInstanceOf(IllegalStateException.class);
            context.requireActive(); // restored outer connection still owns the original capability
            return null;
        });
    }

    @Test
    void nestedTransactionBoundariesCannotSplitAnAtomicCommand() throws Exception {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> commands.execute(() -> identity("outer"), context -> {
            insertCompany(id, "Must roll back");
            return commands.execute(() -> identity("inner"), nested -> null);
        })).isInstanceOf(IllegalStateException.class);
        assertThat(database.scalar("SELECT COUNT(*) FROM company WHERE id='" + id + "'")).isEqualTo("0");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void secondWriterWaitsAndRechecksAfterFirstCommitsOrRollsBack(boolean rollbackFirst) throws Exception {
        UUID id = UUID.randomUUID();
        database.execute("INSERT INTO company(id,code,name,created_at) VALUES('" + id + "','" + id + "','Before',UTC_TIMESTAMP(6))");
        var firstQualified = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var secondQualified = new CountDownLatch(1);
        long before = revision();
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> {
                try {
                    return commands.execute(() -> {
                        firstQualified.countDown();
                        if (!releaseFirst.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("fixture barrier timeout");
                        return identity("first");
                    }, context -> {
                        jdbc.update("UPDATE company SET name='First committed' WHERE id=?", id.toString());
                        if (rollbackFirst) throw new FixtureRollback();
                        return "first";
                    });
                } catch (FixtureRollback expected) { return "rolled back"; }
            });
            assertThat(firstQualified.await(5, TimeUnit.SECONDS)).isTrue();
            Future<String> second = executor.submit(() -> commands.execute(() -> {
                secondQualified.countDown();
                return identity(jdbc.queryForObject("SELECT name FROM company WHERE id=?", String.class, id.toString()));
            }, WriteContext::authority));
            try {
                awaitGuardWait();
                assertThat(secondQualified.getCount()).isEqualTo(1);
            } finally { releaseFirst.countDown(); }
            assertThat(first.get(8, TimeUnit.SECONDS)).isEqualTo(rollbackFirst ? "rolled back" : "first");
            assertThat(second.get(8, TimeUnit.SECONDS)).isEqualTo(rollbackFirst ? "Before" : "First committed");
            assertThat(revision()).isEqualTo(before + (rollbackFirst ? 1 : 2));
        }
    }

    @Test
    void lockTimeoutReturnsRetryableStorageFailureWithoutEnteringQualification() throws Exception {
        var qualified = new java.util.concurrent.atomic.AtomicBoolean();
        try (var holder = database.runtimeConnection()) {
            holder.setAutoCommit(false);
            try (var statement = holder.createStatement()) { statement.executeQuery("SELECT id FROM mutation_guard WHERE id=1 FOR UPDATE").close(); }
            try {
                assertThatThrownBy(() -> commands.execute(() -> {
                    qualified.set(true);
                    return identity("must not run");
                }, context -> null)).isInstanceOfSatisfying(PersistenceFailure.class,
                        error -> assertThat(error.kind()).isEqualTo(PersistenceFailure.Kind.DEPENDENCY_UNAVAILABLE));
                assertThat(qualified).isFalse();
            } finally { holder.rollback(); }
        }
    }

    @Test
    void oneReadKeepsIdentityAndDataInSameSnapshotAndNextReadSeesCommittedChange() throws Exception {
        UUID id = UUID.randomUUID();
        database.execute("INSERT INTO company(id,code,name,created_at) VALUES('" + id + "','" + id + "','Before',UTC_TIMESTAMP(6))");
        try (var executor = Executors.newSingleThreadExecutor()) {
            String result = reads.read(() -> {
                assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
                assertThat(jdbc.queryForObject("SELECT @@transaction_isolation", String.class)).isEqualTo("REPEATABLE-READ");
                return jdbc.queryForObject("SELECT name FROM company WHERE id=?", String.class, id.toString());
            }, identity -> {
                executor.submit(() -> commands.execute(() -> identity("writer"), context -> {
                    jdbc.update("UPDATE company SET name='After' WHERE id=?", id.toString());
                    return null;
                })).get(8, TimeUnit.SECONDS);
                assertThat(identity).isEqualTo("Before");
                return jdbc.queryForObject("SELECT name FROM company WHERE id=?", String.class, id.toString());
            });
            assertThat(result).isEqualTo("Before");
            String next = reads.read(() -> "allowed", ignored -> jdbc.queryForObject(
                    "SELECT name FROM company WHERE id=?", String.class, id.toString()));
            assertThat(next).isEqualTo("After");
        }
    }

    @Test
    void freshReadCannotJoinExistingCommandOrOpenANestedWrite() throws Exception {
        assertThatThrownBy(() -> commands.execute(() -> identity("outer"), context -> reads.read(() -> "inside", v -> v)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reads.read(() -> "outer", v -> commands.execute(() -> identity("inside"), c -> null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void invalidAuditSnapshotCannotLeaveEarlierBusinessOrAuditCommitted() throws Exception {
        UUID id = UUID.randomUUID();
        String correlation = UUID.randomUUID().toString();
        assertThatThrownBy(() -> commands.execute(() -> identity("allowed"), context -> {
            insertCompany(id, "Must roll back");
            audits.append(context, event(id, correlation, List.of()));
            var original = event(id, correlation, List.of());
            audits.append(context, new AuditRecord(original.eventType(), original.targetId(), null, null,
                    original.scope(), original.result(), original.correlationId(), original.reason(),
                    "{\"schemaVersion\":2}", List.of()));
            return null;
        })).isInstanceOf(IllegalArgumentException.class);
        assertThat(database.scalar("SELECT COUNT(*) FROM company WHERE id='" + id + "'")).isEqualTo("0");
        assertThat(database.scalar("SELECT COUNT(*) FROM audit_event WHERE correlation_id='" + correlation + "'")).isEqualTo("0");
    }

    @Test
    void readOnlyBoundaryRejectsWritesAndLeavesNoBusinessRows() throws Exception {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> reads.read(() -> "allowed", ignored -> { insertCompany(id, "Forbidden"); return null; }))
                .isInstanceOf(PersistenceFailure.class);
        assertThat(database.scalar("SELECT COUNT(*) FROM company WHERE id='" + id + "'")).isEqualTo("0");
    }

    @Test
    void losingGuardAfterStartupFailsWithoutRunningQualificationOrRecreatingIt() throws Exception {
        long before = revision();
        var qualified = new java.util.concurrent.atomic.AtomicBoolean();
        database.execute("DELETE FROM mutation_guard");
        try {
            assertThatThrownBy(() -> commands.execute(() -> {
                qualified.set(true); return identity("not reached");
            }, context -> null)).isInstanceOfSatisfying(PersistenceFailure.class,
                    failure -> assertThat(failure.kind()).isEqualTo(PersistenceFailure.Kind.DATA_INTEGRITY_ERROR));
            assertThat(qualified).isFalse();
            assertThat(database.scalar("SELECT COUNT(*) FROM mutation_guard")).isEqualTo("0");
        } finally {
            // Explicit fixture restoration only, never an application recovery path.
            database.execute("INSERT INTO mutation_guard(id,revision) VALUES(1," + before + ")");
        }
    }

    @Test
    void failureAuditIsWrittenOnlyInANewCommandAfterOriginalRollback() throws Exception {
        UUID target = UUID.randomUUID();
        String correlation = UUID.randomUUID().toString();
        long before = revision();
        try {
            commands.execute(() -> identity("original"), context -> {
                insertCompany(target, "Must roll back");
                throw new Exception("fixture checked failure");
            });
            fail("expected original failure");
        } catch (Exception failure) {
            commands.execute(() -> {
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM company WHERE id=?", Long.class, target.toString())).isZero();
                return identity("rechecked after rollback");
            }, context -> audits.append(context, event(target, correlation, List.of())));
        }
        assertThat(database.scalar("SELECT COUNT(*) FROM company WHERE id='" + target + "'")).isEqualTo("0");
        assertThat(database.scalar("SELECT COUNT(*) FROM audit_event WHERE correlation_id='" + correlation + "'")).isEqualTo("1");
        assertThat(revision()).isEqualTo(before + 1);
    }

    private void awaitGuardWait() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (!database.scalar("SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l "
                    + "ON w.REQUESTING_ENGINE_LOCK_ID=l.ENGINE_LOCK_ID WHERE l.OBJECT_SCHEMA=DATABASE() AND l.OBJECT_NAME='mutation_guard'").equals("0")) return;
            Thread.sleep(10);
        }
        fail("second connection never waited on the guard");
    }

    private long revision() throws Exception { return Long.parseLong(database.scalar("SELECT revision FROM mutation_guard WHERE id=1")); }
    private void insertCompany(UUID id, String name) { jdbc.update("INSERT INTO company(id,code,name,created_at) VALUES(?,?,?,UTC_TIMESTAMP(6))", id.toString(), id.toString(), name); }
    private static CommandBoundary.Identity<String> identity(String authority) {
        return new CommandBoundary.Identity<>(authority, new AuditRecord.Actor(AuditRecord.ActorKind.SERVICE, "fixture-service"));
    }
    private static AuditRecord event(UUID target, String correlation, List<AuditRecord.Subject> subjects) {
        return new AuditRecord("FOUNDATION_FIXTURE", target.toString(), null, null,
                new AuditRecord.Scope(AuditRecord.Visibility.GLOBAL, null, null, null),
                "FIXTURE", correlation, "Isolated fixture only", "{\"schemaVersion\":1,\"fixture\":true}", subjects);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixtureClock {
        @Bean Clock transactionFixtureClock() {
            return Clock.fixed(Instant.parse("2026-10-18T03:04:05.123456789Z"), ZoneOffset.UTC);
        }
    }
    private static class FixtureRollback extends Exception { }
}
