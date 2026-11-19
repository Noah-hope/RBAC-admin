package com.rbac.rbac1.service;

import com.rbac.rbac1.entity.OperationLog;
import com.rbac.rbac1.entity.OperationLogTarget;
import com.rbac.rbac1.entity.UserAccount;
import com.rbac.rbac1.mapper.OperationLogMapper;
import com.rbac.rbac1.mapper.OperationLogTargetMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 写入操作记录。
 *
 * <p>成功记录必须在业务事务内写入（MANDATORY），与业务数据一起提交或回滚；失败记录使用独立事务，
 * 业务事务回滚后仍能保留。对象快照记下事件发生时的名称和所属部门，后续改名或调部门不影响历史记录的可见范围。
 */
@Service
@RequiredArgsConstructor
public class OperationLogService {

  private final OperationLogMapper logs;

  private final OperationLogTargetMapper targets;

  /**
   * 记录只有总管理员可见的全局变更，例如应用、权限、角色和 API 的修改。
   *
   * @param actor 操作人
   * @param action 动作名称
   * @param detail 变更详情
   * @param targetType 对象类型，见 {@link OperationLogTarget} 中的常量
   * @param targetId 对象 ID
   * @param targetName 对象名称
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordGlobalChange(
      SessionUser actor,
      String action,
      String detail,
      String targetType,
      long targetId,
      String targetName) {
    OperationLog log = saveLog(actor, action, detail, OperationLog.GLOBAL);
    targets.save(target(log.getId(), 1, targetType, targetId, targetName, null));
  }

  /**
   * 记录针对单个用户的变更，部门管理员可按事件发生时的部门看到。
   *
   * @param actor 操作人
   * @param action 动作名称
   * @param detail 变更详情
   * @param user 被操作的用户
   * @param departmentIdAtEvent 事件发生时用户所属部门；调部门时由调用方决定记旧部门还是新部门
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordUserChange(
      SessionUser actor, String action, String detail, UserAccount user, long departmentIdAtEvent) {
    OperationLog log = saveLog(actor, action, detail, OperationLog.USER_SCOPE);
    targets.save(
        target(
            log.getId(),
            1,
            OperationLogTarget.USER,
            user.getId(),
            user.getName(),
            departmentIdAtEvent));
  }

  /**
   * 记录一次影响多个用户的变更（如批量调整角色），每个用户一个对象快照，按传入顺序编号。
   *
   * @param actor 操作人
   * @param action 动作名称
   * @param detail 变更详情
   * @param users 被操作的用户，快照取各自当前所属部门
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordUserChanges(
      SessionUser actor, String action, String detail, List<UserAccount> users) {
    OperationLog log = saveLog(actor, action, detail, OperationLog.USER_SCOPE);
    List<OperationLogTarget> snapshots = new ArrayList<>();
    for (UserAccount user : users) {
      snapshots.add(
          target(
              log.getId(),
              snapshots.size() + 1,
              OperationLogTarget.USER,
              user.getId(),
              user.getName(),
              user.getDepartmentId()));
    }
    targets.saveAll(snapshots);
  }

  /**
   * 在独立事务中记录失败的管理操作，业务事务回滚后仍然保留。
   *
   * @param actor 操作人
   * @param method HTTP 方法
   * @param path 请求路径，同时作为对象名称
   * @param reason 失败原因
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordFailure(SessionUser actor, String method, String path, String reason) {
    OperationLog log = saveLog(actor, "操作失败", method + " " + path + "，" + reason, OperationLog.GLOBAL);
    log.setResult(OperationLog.FAILURE);
    targets.save(target(log.getId(), 1, OperationLogTarget.API, null, path, null));
  }

  /** 保存记录主体，结果默认为成功，发生时间取 UTC。 */
  private OperationLog saveLog(SessionUser actor, String action, String detail, String scope) {
    OperationLog log = new OperationLog();
    log.setOccurredAt(LocalDateTime.now(ZoneOffset.UTC));
    log.setActorUserId(actor.id());
    log.setActorName(actor.name());
    log.setAction(action);
    log.setScope(scope);
    log.setDetail(detail);
    log.setResult(OperationLog.SUCCESS);
    return logs.save(log);
  }

  /** 组装一条对象快照。 */
  private static OperationLogTarget target(
      long logId,
      int number,
      String targetType,
      Long targetId,
      String targetName,
      Long departmentIdAtEvent) {
    OperationLogTarget target = new OperationLogTarget();
    target.setOperationLogId(logId);
    target.setTargetNo(number);
    target.setTargetType(targetType);
    target.setTargetId(targetId);
    target.setTargetName(targetName);
    target.setDepartmentIdAtEvent(departmentIdAtEvent);
    return target;
  }
}
