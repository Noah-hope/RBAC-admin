package com.rbac.rbac1ddd.application.audit;

import com.rbac.rbac1ddd.application.shared.PageResult;
import java.util.Set;

/**
 * 读模型端口：搜索操作记录，由基础设施层实现。
 *
 * <p>两种查法都按发生时间倒序、同一时间按记录 ID 倒序，页码从 1 开始。
 * 关键字为空字符串时不筛选，否则匹配操作人、操作名称、操作内容或可见对象的名称。
 */
public interface OperationLogReader {

  /**
   * 搜索全部记录，列出每条记录的全部对象，供总管理员查看。
   *
   * @param keyword 搜索关键字，空字符串表示不筛选
   * @param page 页码，从 1 开始
   * @param pageSize 每页条数
   * @return 当前页记录和总数
   */
  PageResult<OperationLogResult> searchAll(String keyword, int page, int pageSize);

  /**
   * 按部门范围搜索用户范围记录，供部门管理员查看。
   *
   * <p>只返回用户范围（USER_SCOPE）的记录，且至少一个对象在操作发生时属于给定部门；
   * 每条记录只列出操作时在给定部门内的对象，关键字也只匹配这些对象的名称。
   *
   * @param departmentIds 可查看的部门 ID，不为空
   * @param keyword 搜索关键字，空字符串表示不筛选
   * @param page 页码，从 1 开始
   * @param pageSize 每页条数
   * @return 当前页记录和总数
   */
  PageResult<OperationLogResult> searchInDepartments(
      Set<Long> departmentIds, String keyword, int page, int pageSize);
}
