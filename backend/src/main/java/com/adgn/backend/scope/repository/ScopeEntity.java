package com.adgn.backend.scope.repository;

import com.adgn.backend.core.persistence.UtcInstantConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

/** Read mapping only. Future scope commands own business-version changes. */
@Entity(name="ScopeReadEntity")
@Table(name="management_scope")
@Immutable
public class ScopeEntity {
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(length=36) private String id;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="company_id",length=36) private String companyId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="department_id",length=36) private String departmentId;
    @Column(name="scope_version") private long version;
    @Convert(converter=UtcInstantConverter.class) @Column(name="created_at") private Instant createdAt;
    protected ScopeEntity() { }
    public String getId(){return id;}
    public String getCompanyId(){return companyId;}
    public String getDepartmentId(){return departmentId;}
    public long getVersion(){return version;}
    public Instant getCreatedAt(){return createdAt;}
}
