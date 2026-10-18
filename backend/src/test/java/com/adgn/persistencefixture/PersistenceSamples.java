package com.adgn.persistencefixture;

import com.adgn.backend.core.persistence.UtcInstantConverter;
import com.adgn.backend.core.persistence.VersionedUuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;

import java.time.Instant;
import java.util.UUID;

/** Mapping fixtures only: outside the application's scan root and absent from the runtime jar. */
public final class PersistenceSamples {
    private PersistenceSamples() { }

    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = PersistenceSamples.class)
    public static class MappingConfiguration { }

    @Entity(name = "PersistenceSystemSample")
    @Table(name = "business_system")
    public static class SystemSample extends VersionedUuidEntity {
        @Column(nullable = false, length = 100) public String code;
        @Column(nullable = false, length = 100) public String name;
        @Column(nullable = false, length = 500) public String description = "";
        @Column(nullable = false) public Byte enabled = 1;
        @Column(name = "version", nullable = false) public long businessVersion = 41;
        @Column(name = "catalog_version", nullable = false) public long catalogVersion = 73;
        @Convert(converter = UtcInstantConverter.class)
        @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
        public Instant createdAt;

        protected SystemSample() { }
        public SystemSample(UUID id, Instant createdAt) {
            super(id);
            this.code = "FIXTURE_" + id;
            this.name = "Initial";
            this.createdAt = createdAt;
        }
    }

    @Entity(name = "PersistenceAppointmentSample")
    @Table(name = "admin_appointment")
    public static class AppointmentSample extends VersionedUuidEntity {
        @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "scope_id", nullable = false, length = 36)
        public UUID scopeId;
        @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "account_id", nullable = false, length = 36)
        public UUID accountId;
        @Column(nullable = false, length = 32) public String status = "ACTIVE";
        @Column(name = "appointment_version", nullable = false) public long appointmentVersion = 11;
        @Convert(converter = UtcInstantConverter.class)
        @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
        public Instant createdAt;
        @Convert(converter = UtcInstantConverter.class)
        @Column(name = "ended_at", columnDefinition = "datetime(6)") public Instant endedAt;
        @Column(name = "ended_reason", length = 500) public String endedReason;
        @Column(name = "active_slot", insertable = false, updatable = false) public Byte activeSlot;

        protected AppointmentSample() { }
        public AppointmentSample(UUID id, UUID scopeId, UUID accountId, Instant createdAt) {
            super(id);
            this.scopeId = scopeId;
            this.accountId = accountId;
            this.createdAt = createdAt;
        }
    }
}
