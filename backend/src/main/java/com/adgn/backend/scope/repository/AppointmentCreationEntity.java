package com.adgn.backend.scope.repository;

import com.adgn.backend.core.persistence.UtcInstantConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity(name="AppointmentCreationEntity")
@Table(name="admin_appointment")
public class AppointmentCreationEntity {
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(length=36) private String id;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="scope_id",length=36) private String scopeId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="account_id",length=36) private String accountId;
    @Column(length=32) private String status="ACTIVE";
    @Column(name="appointment_version") private long version=1;
    @Version @Column(name="row_version") private Long rowVersion;
    @Convert(converter=UtcInstantConverter.class) @Column(name="created_at") private Instant createdAt;
    protected AppointmentCreationEntity(){ }
    AppointmentCreationEntity(String id,String scope,String account,Instant now){this.id=id;this.scopeId=scope;this.accountId=account;this.createdAt=now;}
}
