package com.adgn.backend.scope.repository;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity(name="AppointmentReadEntity")
@Table(name="admin_appointment")
@Immutable
public class AppointmentEntity {
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(length=36) private String id;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="scope_id",length=36) private String scopeId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="account_id",length=36) private String accountId;
    @Column(length=32) private String status;
    @Column(name="appointment_version") private long version;
    protected AppointmentEntity() { }
}
