package com.rbac.rbac1ddd.infrastructure.persistence;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.rbac.rbac1ddd.application.shared.OperationRecord;
import com.rbac.rbac1ddd.application.shared.OperationRecorder;
import com.rbac.rbac1ddd.application.shared.OperationTarget;
import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.infrastructure.persistence.dao.OperationLogDao;
import com.rbac.rbac1ddd.infrastructure.persistence.dao.OperationLogTargetDao;
import com.rbac.rbac1ddd.infrastructure.persistence.po.OperationLogPo;
import com.rbac.rbac1ddd.infrastructure.persistence.po.OperationLogTargetPo;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 把操作记录写入 sys_operation_log 和 sys_operation_log_target，用 CloneFactory 完成对象拷贝。 */
@Repository
@RequiredArgsConstructor
class JpaOperationRecorder implements OperationRecorder {
  private final OperationLogDao logs;
  private final OperationLogTargetDao targets;

  /** 记录全局可见的成功操作，必须与业务数据在同一事务内提交。 */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordGlobal(Operator actor, String action, String detail, OperationTarget target) {
    save(OperationRecord.success(actor, action, OperationRecord.GLOBAL, detail), List.of(target));
  }

  /** 记录按用户管理范围可见的成功操作，必须与业务数据在同一事务内提交。 */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void recordUserScope(
      Operator actor, String action, String detail, List<OperationTarget> targetList) {
    save(OperationRecord.success(actor, action, OperationRecord.USER_SCOPE, detail), targetList);
  }

  /** 在独立事务中记录失败操作，业务事务回滚时失败记录仍然保留。 */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordFailure(Operator actor, String method, String route, String reason) {
    save(
        OperationRecord.failure(actor, method + " " + route + "，" + reason),
        List.of(OperationTarget.api(route)));
  }

  /** 先保存操作记录取得 ID，再按顺序为每个对象编号（从 1 开始）并批量保存。 */
  private void save(OperationRecord record, List<OperationTarget> targetList) {
    OperationLogPo logPo = logs.save(CloneFactory.copy(new OperationLogPo(), record));
    List<OperationLogTargetPo> rows = new ArrayList<>();
    for (OperationTarget target : targetList) {
      OperationLogTargetPo row = CloneFactory.copy(new OperationLogTargetPo(), target);
      row.setOperationLogId(logPo.getId());
      row.setTargetNo(rows.size() + 1);
      rows.add(row);
    }
    targets.saveAll(rows);
  }
}
