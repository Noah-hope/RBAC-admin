package com.rbac.rbac0.mapper;

import java.util.List;

import com.rbac.rbac0.entity.OperationLogTarget;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationLogTargetMapper extends JpaRepository<OperationLogTarget, OperationLogTarget.Key> {

    List<OperationLogTarget> findByOperationLogIdInOrderByTargetNoAsc(List<Long> logIds);

    List<OperationLogTarget> findByOperationLogIdInAndDepartmentIdAtEventInOrderByTargetNoAsc(
            List<Long> logIds, List<Long> departmentIds);
}
