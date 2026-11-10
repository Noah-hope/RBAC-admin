package com.rbac.rbac0.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sys_operation_log")
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public LocalDateTime occurredAt;
    public Long actorUserId;
    public String actorName;
    public String action;
    public String scope;

    @Column(columnDefinition = "TEXT")
    public String detail;

    public String result;
}
