package com.rbac.rbac1ddd.infrastructure.persistence.dao;

import com.rbac.rbac1ddd.infrastructure.persistence.po.OperationLogTargetPo;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** sys_operation_log_target 表的 Spring Data 访问接口。 */
public interface OperationLogTargetDao
    extends JpaRepository<OperationLogTargetPo, OperationLogTargetPo.Key> {

  /** 读取多条记录的全部对象，按对象序号升序。 */
  List<OperationLogTargetPo> findByOperationLogIdInOrderByTargetNoAsc(Collection<Long> logIds);

  /** 读取多条记录中操作时属于给定部门的对象，按对象序号升序。 */
  List<OperationLogTargetPo> findByOperationLogIdInAndDepartmentIdAtEventInOrderByTargetNoAsc(
      Collection<Long> logIds, Collection<Long> departmentIds);
}
