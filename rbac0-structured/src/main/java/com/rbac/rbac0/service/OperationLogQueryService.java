package com.rbac.rbac0.service;

import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

import com.rbac.rbac0.dto.OperationLogDtos.OperationLogPage;
import com.rbac.rbac0.dto.OperationLogDtos.OperationLogView;
import com.rbac.rbac0.dto.SessionDtos.SessionUser;
import com.rbac.rbac0.mapper.OperationLogMapper;
import com.rbac.rbac0.mapper.OperationLogTargetMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.rbac.rbac0.service.DepartmentService.isTotalAdmin;

@Service
public class OperationLogQueryService {

    private final DepartmentService departments;
    private final OperationLogMapper logs;
    private final OperationLogTargetMapper targets;

    public OperationLogQueryService(DepartmentService departments, OperationLogMapper logs,
                                    OperationLogTargetMapper targets) {
        this.departments = departments;
        this.logs = logs;
        this.targets = targets;
    }

    @Transactional(readOnly = true)
    public OperationLogPage list(SessionUser actor, int page, int pageSize, String keyword) {
        boolean totalAdmin = isTotalAdmin(actor);
        var departmentIds = totalAdmin ? List.<Long>of() : departments.managedDepartments(actor, null).stream()
                .map(department -> department.id).toList();
        var pageable = PageRequest.of(page - 1, pageSize);
        var matches = totalAdmin ? logs.searchAll(keyword, pageable)
                : logs.searchByDepartmentScope(departmentIds, keyword, pageable);
        var logIds = matches.stream().map(log -> log.id).toList();
        if (logIds.isEmpty()) {
            return new OperationLogPage(List.of(), page, pageSize, matches.getTotalElements());
        }

        var visibleTargets = totalAdmin ? targets.findByOperationLogIdInOrderByTargetNoAsc(logIds)
                : targets.findByOperationLogIdInAndDepartmentIdAtEventInOrderByTargetNoAsc(logIds, departmentIds);
        var namesByLog = visibleTargets.stream().collect(Collectors.groupingBy(target -> target.operationLogId,
                Collectors.mapping(target -> target.targetName, Collectors.toList())));
        var items = matches.stream().map(log -> new OperationLogView(log.id, log.occurredAt.toInstant(ZoneOffset.UTC),
                log.actorName, log.action, namesByLog.getOrDefault(log.id, List.of()), log.detail, log.result)).toList();
        return new OperationLogPage(items, page, pageSize, matches.getTotalElements());
    }
}
