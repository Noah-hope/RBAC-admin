package com.adgn.backend;

import com.adgn.backend.testsupport.IsolatedMySqlDatabase;
import com.adgn.persistencefixture.PersistenceSamples.AppointmentSample;
import com.adgn.persistencefixture.PersistenceSamples.MappingConfiguration;
import com.adgn.persistencefixture.PersistenceSamples.SystemSample;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JpaMappingMySqlCheck {
    private IsolatedMySqlDatabase database;
    private ConfigurableApplicationContext context;
    private EntityManagerFactory factory;
    private static final Instant CREATED = Instant.parse("2026-10-18T02:03:04.123456789Z");

    @BeforeAll
    void startMappedApplication() throws Exception {
        database = new IsolatedMySqlDatabase();
        context = database.start(BackendApplication.class, MappingConfiguration.class);
        factory = context.getBean(EntityManagerFactory.class);
    }

    @AfterAll
    void stopMappedApplication() throws Exception {
        if (context != null) context.close();
        if (database != null) database.close();
    }

    @Test
    void preallocatedUuidUsesPersistAndRoundTripsAsCharWithUtcMicroseconds() throws Exception {
        UUID id = UUID.randomUUID();
        var sample = new SystemSample(id, CREATED);
        try (var entityManager = factory.createEntityManager()) {
            entityManager.getTransaction().begin();
            var saved = new SimpleJpaRepository<>(SystemSample.class, entityManager).save(sample);
            assertThat(saved).as("new preallocated IDs must use persist rather than merge").isSameAs(sample);
            entityManager.flush();
            entityManager.getTransaction().commit();
        }
        assertThat(database.scalar("SELECT id FROM business_system WHERE id='" + id + "'"))
                .isEqualTo(id.toString());
        assertThat(database.scalar("SELECT DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s.%f') "
                + "FROM business_system WHERE id='" + id + "'"))
                .isEqualTo("2026-10-18 02:03:04.123456");
        try (var entityManager = factory.createEntityManager()) {
            var loaded = entityManager.find(SystemSample.class, id);
            assertThat(loaded.getId()).isEqualTo(id);
            assertThat(loaded.createdAt).isEqualTo(Instant.parse("2026-10-18T02:03:04.123456Z"));
            assertThat(loaded.getRowVersion()).isZero();
            assertThat(loaded.businessVersion).isEqualTo(41);
            assertThat(loaded.catalogVersion).isEqualTo(73);
        }
    }

    @Test
    void rejectsStaleUpdateWithoutOverwritingCommittedDataOrBusinessVersions() throws Exception {
        UUID id = insertSystem();
        try (var first = factory.createEntityManager(); var stale = factory.createEntityManager()) {
            first.getTransaction().begin();
            stale.getTransaction().begin();
            var winner = first.find(SystemSample.class, id);
            var loser = stale.find(SystemSample.class, id);
            winner.name = "Committed winner";
            first.getTransaction().commit();
            loser.name = "Stale loser";
            try {
                assertThatThrownBy(stale::flush).isInstanceOf(OptimisticLockException.class);
            } finally { if (stale.getTransaction().isActive()) stale.getTransaction().rollback(); }
        }
        assertThat(database.scalar("SELECT name FROM business_system WHERE id='" + id + "'"))
                .isEqualTo("Committed winner");
        assertThat(database.scalar("SELECT row_version FROM business_system WHERE id='" + id + "'"))
                .isEqualTo("1");
        assertThat(database.scalar("SELECT version FROM business_system WHERE id='" + id + "'"))
                .isEqualTo("41");
        assertThat(database.scalar("SELECT catalog_version FROM business_system WHERE id='" + id + "'"))
                .isEqualTo("73");
    }

    @Test
    void noChangeFlushDoesNotAdvanceEitherStorageOrBusinessVersion() throws Exception {
        UUID id = insertSystem();
        try (var entityManager = factory.createEntityManager()) {
            entityManager.getTransaction().begin();
            entityManager.find(SystemSample.class, id);
            entityManager.flush();
            entityManager.getTransaction().commit();
        }
        assertThat(database.scalar("SELECT row_version FROM business_system WHERE id='" + id + "'"))
                .isEqualTo("0");
        assertThat(database.scalar("SELECT version FROM business_system WHERE id='" + id + "'"))
                .isEqualTo("41");
    }

    @Test
    void generatedActiveSlotIsComputedByMySqlAndNeverWrittenByJpa() throws Exception {
        assertThatCode(this::exerciseGeneratedSlot).doesNotThrowAnyException();
    }

    @Test
    void aFreshObjectWithAnExistingUuidCannotMergeOverTheStoredRecord() throws Exception {
        UUID id = insertSystem();
        try (var entityManager = factory.createEntityManager()) {
            entityManager.getTransaction().begin();
            var duplicate = new SystemSample(id, CREATED);
            duplicate.name = "Must not overwrite";
            try {
                assertThatThrownBy(() -> {
                    new SimpleJpaRepository<>(SystemSample.class, entityManager).saveAndFlush(duplicate);
                }).isInstanceOf(PersistenceException.class);
            } finally { if (entityManager.getTransaction().isActive()) entityManager.getTransaction().rollback(); }
        }
        assertThat(database.scalar("SELECT name FROM business_system WHERE id='" + id + "'"))
                .isEqualTo("Initial");
    }

    private void exerciseGeneratedSlot() throws Exception {
        UUID company = UUID.randomUUID(), department = UUID.randomUUID();
        UUID account = UUID.randomUUID(), scope = UUID.randomUUID(), appointment = UUID.randomUUID();
        database.execute("INSERT INTO company(id,code,name,created_at) VALUES('" + company + "','C_FIX','Fixture',UTC_TIMESTAMP(6))");
        database.execute("INSERT INTO department(id,company_id,code,name,created_at) VALUES('" + department
                + "','" + company + "','D_FIX','Fixture',UTC_TIMESTAMP(6))");
        database.execute("INSERT INTO admin_account(id,login_name,password_hash,is_super_admin,created_at) VALUES('"
                + account + "','fixture-only','fixture-not-a-real-credential',1,UTC_TIMESTAMP(6))");
        database.execute("INSERT INTO management_scope(id,company_id,department_id,created_at) VALUES('" + scope
                + "','" + company + "','" + department + "',UTC_TIMESTAMP(6))");
        try (var entityManager = factory.createEntityManager()) {
            entityManager.getTransaction().begin();
            entityManager.persist(new AppointmentSample(appointment, scope, account, CREATED));
            entityManager.flush();
            entityManager.clear();
            var loaded = entityManager.find(AppointmentSample.class, appointment);
            assertThat(loaded.activeSlot).isEqualTo((byte) 1);
            loaded.activeSlot = 99; // attempting to dirty a read-only generated value must not write it
            loaded.status = "REVOKED";
            loaded.endedAt = Instant.parse("2026-10-18T04:00:00Z");
            loaded.endedReason = "Mapping fixture only";
            loaded.appointmentVersion = 12; // business version advances explicitly
            entityManager.flush();
            entityManager.refresh(loaded);
            assertThat(loaded.activeSlot).isNull();
            assertThat(loaded.getRowVersion()).isEqualTo(1);
            assertThat(loaded.appointmentVersion).isEqualTo(12);
            entityManager.getTransaction().commit();
        }
    }

    private UUID insertSystem() {
        UUID id = UUID.randomUUID();
        try (EntityManager entityManager = factory.createEntityManager()) {
            entityManager.getTransaction().begin();
            entityManager.persist(new SystemSample(id, CREATED));
            entityManager.getTransaction().commit();
        }
        return id;
    }
}
