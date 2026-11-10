package com.rbac.rbac0.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.rbac.rbac0.dto.SessionDtos.SessionUser;
import com.rbac.rbac0.entity.OperationLog;
import com.rbac.rbac0.entity.OperationLogTarget;
import com.rbac.rbac0.mapper.OperationLogMapper;
import com.rbac.rbac0.mapper.OperationLogTargetMapper;
import org.springframework.stereotype.Service;

@Service
public class OperationLogService {

    private final OperationLogMapper logs;
    private final OperationLogTargetMapper targets;

    public OperationLogService(OperationLogMapper logs, OperationLogTargetMapper targets) {
        this.logs = logs;
        this.targets = targets;
    }

    public void recordGlobalChange(SessionUser actor, String action, String detail, String targetType,
                                   long targetId, String targetName) {
        var log = new OperationLog();
        log.occurredAt = LocalDateTime.now(ZoneOffset.UTC);
        log.actorUserId = actor.id();
        log.actorName = actor.name();
        log.action = action;
        log.scope = "GLOBAL";
        log.detail = detail;
        log.result = "SUCCESS";
        logs.save(log);

        var target = new OperationLogTarget();
        target.operationLogId = log.id;
        target.targetNo = 1;
        target.targetType = targetType;
        target.targetId = targetId;
        target.targetName = targetName;
        targets.save(target);
    }
}
