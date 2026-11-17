package com.rbac.rbac1ddd.infrastructure.persistence.dao;

import com.rbac.rbac1ddd.infrastructure.persistence.po.OperationLogPo;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * sys_operation_log 表的 Spring Data 访问接口。
 *
 * <p>搜索语句与 rbac1-structured 的 OperationLogMapper 相同：用 locate 匹配关键字，空关键字不筛选，
 * 按发生时间倒序、同一时间按 ID 倒序。
 */
public interface OperationLogDao extends JpaRepository<OperationLogPo, Long> {

  /** 关键字匹配操作人姓名、操作名称或操作内容。 */
  String MATCH_LOG_FIELDS =
      """
      (:keyword = '' or locate(:keyword, entry.actorName) > 0
          or locate(:keyword, entry.action) > 0 or locate(:keyword, entry.detail) > 0)
      """;

  /**
   * 搜索全部记录，关键字还可以匹配任一对象名称。
   *
   * @param keyword 搜索关键字，空字符串表示不筛选
   * @param pageable 分页参数
   * @return 当前页记录和总数
   */
  @Query(
      "select entry from OperationLogPo entry where ("
          + MATCH_LOG_FIELDS
          + """
          or exists (
              select 1 from OperationLogTargetPo target
              where target.operationLogId = entry.id and locate(:keyword, target.targetName) > 0
          ))
          order by entry.occurredAt desc, entry.id desc
          """)
  Page<OperationLogPo> searchAll(String keyword, Pageable pageable);

  /**
   * 搜索指定范围的记录：至少一个对象在操作时属于给定部门，关键字只匹配这些对象的名称。
   *
   * @param scope 记录可见范围，只查用户范围记录
   * @param departmentIds 可查看的部门 ID
   * @param keyword 搜索关键字，空字符串表示不筛选
   * @param pageable 分页参数
   * @return 当前页记录和总数
   */
  @Query(
      """
      select entry from OperationLogPo entry
      where entry.scope = :scope and exists (
          select 1 from OperationLogTargetPo target
          where target.operationLogId = entry.id and target.departmentIdAtEvent in :departmentIds
            and (
      """
          + MATCH_LOG_FIELDS
          + """
                 or locate(:keyword, target.targetName) > 0)
      )
      order by entry.occurredAt desc, entry.id desc
      """)
  Page<OperationLogPo> searchByDepartments(
      String scope, Collection<Long> departmentIds, String keyword, Pageable pageable);
}
