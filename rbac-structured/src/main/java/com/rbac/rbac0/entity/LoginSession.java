package com.rbac.rbac0.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "sys_session")
public class LoginSession {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(length = 32)
    public byte[] tokenHash;

    @Column(nullable = false)
    public Long userId;

    @Column(nullable = false)
    public LocalDateTime expiresAt;
}
