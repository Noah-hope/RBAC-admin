package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.OperationLogTarget;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** sys_operation_log_target 表的数据访问接口：读取操作记录涉及的对象快照。 */
public interface OperationLogTargetMapper
    extends JpaRepository<OperationLogTarget, OperationLogTarget.Key> {

  /**
   * 总管理员查看日志时，读取一页日志涉及的全部对象，按对象序号排列。
   *
   * @param logIds 当前页的日志 ID
   * @return 这些日志的全部对象快照
   */
  List<OperationLogTarget> findByOperationLogIdInOrderByTargetNoAsc(List<Long> logIds);

  /**
   * 部门管理员查看日志时，只读取发生时位于其管理部门内的对象，避免泄露范围外的对象名称。
   *
   * @param logIds 当前页的日志 ID
   * @param departmentIds 管理范围内的部门 ID
   * @return 范围内的对象快照，按对象序号排列
   */
  List<OperationLogTarget> findByOperationLogIdInAndDepartmentIdAtEventInOrderByTargetNoAsc(
      List<Long> logIds, List<Long> departmentIds);
}
