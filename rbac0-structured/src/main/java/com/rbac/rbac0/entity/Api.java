package com.rbac.rbac0.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_api")
public class Api {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Long applicationId;

    @Column(nullable = false, length = 128)
    public String operationId;

    @Column(nullable = false, length = 10)
    public String httpMethod;

    @Column(nullable = false, length = 255)
    public String path;
}
