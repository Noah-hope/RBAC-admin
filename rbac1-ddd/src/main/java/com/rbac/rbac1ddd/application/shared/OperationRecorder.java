package com.rbac.rbac1ddd.application.shared;

import com.rbac.rbac1ddd.application.auth.Operator;
import java.util.List;

/** 操作记录端口。成功记录与业务数据同一事务提交；失败记录单独提交，不随业务回滚。 */
public interface OperationRecorder {
  /** 全局配置变更（角色等），只有总管理员可见。 */
  void recordGlobal(Operator actor, String action, String detail, OperationTarget target);

  /** 与用户相关的变更，部门管理员按操作时的部门筛选可见。 */
  void recordUserScope(Operator actor, String action, String detail, List<OperationTarget> targets);

  /**
   * 记录失败的写操作。
   *
   * @param method HTTP 方法
   * @param route 请求的路由
   * @param reason 失败原因
   */
  void recordFailure(Operator actor, String method, String route, String reason);
}
