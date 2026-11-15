package com.rbac.rbac0.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import com.rbac.rbac0.dto.SessionDtos.SessionUser;
import com.rbac.rbac0.entity.OperationLog;
import com.rbac.rbac0.entity.OperationLogTarget;
import com.rbac.rbac0.entity.UserAccount;
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
        var log = saveLog(actor, action, detail, "GLOBAL");
        targets.save(target(log.id, 1, targetType, targetId, targetName, null));
    }

    public void recordUserChange(SessionUser actor, String action, String detail, UserAccount user, long departmentIdAtEvent) {
        var log = saveLog(actor, action, detail, "USER_SCOPE");
        targets.save(target(log.id, 1, "USER", user.id, user.name, departmentIdAtEvent));
    }

    public void recordUserChanges(SessionUser actor, String action, String detail, List<UserAccount> users) {
        var log = saveLog(actor, action, detail, "USER_SCOPE");
        var snapshots = new ArrayList<OperationLogTarget>();
        for (var user : users) {
            snapshots.add(target(log.id, snapshots.size() + 1, "USER", user.id, user.name, user.departmentId));
        }
        targets.saveAll(snapshots);
    }

    private OperationLog saveLog(SessionUser actor, String action, String detail, String scope) {
        var log = new OperationLog();
        log.occurredAt = LocalDateTime.now(ZoneOffset.UTC);
        log.actorUserId = actor.id();
        log.actorName = actor.name();
        log.action = action;
        log.scope = scope;
        log.detail = detail;
        log.result = "SUCCESS";
        return logs.save(log);
    }

    private static OperationLogTarget target(long logId, int number, String targetType, long targetId,
                                             String targetName, Long departmentIdAtEvent) {
        var target = new OperationLogTarget();
        target.operationLogId = logId;
        target.targetNo = number;
        target.targetType = targetType;
        target.targetId = targetId;
        target.targetName = targetName;
        target.departmentIdAtEvent = departmentIdAtEvent;
        return target;
    }
}
