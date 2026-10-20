package com.adgn.backend.scope.repository;

import com.adgn.backend.core.persistence.UtcInstantConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.*;

/** Creation mapping; BE-08's immutable query mapping remains read-only. */
@Entity(name="ScopeCreationEntity")
@Table(name="management_scope")
public class ScopeCreationEntity {
    @Id @JdbcTypeCode(SqlTypes.CHAR) @Column(length=36) private String id;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="company_id",length=36) private String companyId;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="department_id",length=36) private String departmentId;
    @Column(name="scope_version") private long version=1;
    @Version @Column(name="row_version") private Long rowVersion;
    @Convert(converter=UtcInstantConverter.class) @Column(name="created_at") private Instant createdAt;
    @ElementCollection
    @CollectionTable(name="scope_system",joinColumns=@JoinColumn(name="scope_id",columnDefinition="char(36)"))
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name="system_id",length=36)
    private Set<String> systems=new HashSet<>();
    protected ScopeCreationEntity(){ }
    ScopeCreationEntity(String id,String company,String department,Collection<String> systems,Instant now){
        this.id=id;this.companyId=company;this.departmentId=department;this.systems.addAll(systems);this.createdAt=now;
    }
}
