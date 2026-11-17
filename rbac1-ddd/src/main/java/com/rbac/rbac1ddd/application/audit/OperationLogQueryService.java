package com.rbac.rbac1ddd.application.audit;

import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.application.shared.PageResult;
import com.rbac.rbac1ddd.domain.department.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 查询服务：查看或搜索操作记录。
 *
 * <p>总管理员查看全部记录；部门管理员只查看本部门及下级部门用户的管理记录，
 * 以操作发生时用户所属的部门判断，全局记录不可见。管理范围由 {@link Operator} 计算。
 */
@Service
@RequiredArgsConstructor
public class OperationLogQueryService {

  private final DepartmentRepository departments;
  private final OperationLogReader logs;

  /**
   * 分页搜索操作者可以查看的操作记录。
   *
   * @param operator 当前操作者，必须是管理员；部门管理员必须有所属部门
   * @param keyword 搜索关键字，空字符串表示不筛选
   * @param page 页码，从 1 开始
   * @param pageSize 每页条数
   * @return 当前页记录和总数
   */
  @Transactional(readOnly = true)
  public PageResult<OperationLogResult> list(
      Operator operator, String keyword, int page, int pageSize) {
    if (operator.isTotalAdmin()) return logs.searchAll(keyword, page, pageSize);
    return logs.searchInDepartments(
        operator.manageableDepartments(departments.loadTree(), null), keyword, page, pageSize);
  }
}
