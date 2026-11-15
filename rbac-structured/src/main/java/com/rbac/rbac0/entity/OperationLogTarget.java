package com.rbac.rbac0.entity;

import java.io.Serializable;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "sys_operation_log_target")
@IdClass(OperationLogTarget.Key.class)
public class OperationLogTarget {

    @Id
    public Long operationLogId;

    @Id
    public Integer targetNo;

    public String targetType;
    public Long targetId;
    public String targetName;
    public Long departmentIdAtEvent;

    public record Key(Long operationLogId, Integer targetNo) implements Serializable {}
}
