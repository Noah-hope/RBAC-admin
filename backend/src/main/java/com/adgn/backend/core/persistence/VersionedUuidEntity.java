package com.adgn.backend.core.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;

/** Only for tables with a dedicated row_version. API business versions remain explicit fields. */
@MappedSuperclass
public abstract class VersionedUuidEntity {
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private UUID id;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    protected VersionedUuidEntity() { }
    protected VersionedUuidEntity(UUID id) { this.id = id; }
    public UUID getId() { return id; }
    public Long getRowVersion() { return rowVersion; }
}
