package com.rbac.rbac0.mapper;

import com.rbac.rbac0.entity.OperationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationLogMapper extends JpaRepository<OperationLog, Long> {}
