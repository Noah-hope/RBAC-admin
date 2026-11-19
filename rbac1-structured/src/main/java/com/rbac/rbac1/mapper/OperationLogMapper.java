package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.OperationLog;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** sys_operation_log 表的数据访问接口：按查看人的可见范围分页检索操作记录，新记录在前。 */
public interface OperationLogMapper extends JpaRepository<OperationLog, Long> {

  /** 关键字匹配条件的 JPQL 片段：关键字为空串时不过滤，否则匹配操作人、操作类型或操作内容的子串。 */
  String MATCH_LOG_FIELDS = """
          (:keyword = '' or locate(:keyword, entry.actorName) > 0
              or locate(:keyword, entry.action) > 0 or locate(:keyword, entry.detail) > 0)
          """;

  /**
   * 总管理员查看日志：在全部记录中检索，关键字还可以匹配任一操作对象的名称。
   *
   * @param keyword 关键字，空串表示不过滤
   * @param pageable 分页参数
   * @return 当前页的操作记录，按发生时间和 ID 倒序
   */
  @Query("select entry from OperationLog entry where (" + MATCH_LOG_FIELDS + """
          or exists (
              select 1 from OperationLogTarget target
              where target.operationLogId = entry.id and locate(:keyword, target.targetName) > 0
          ))
          order by entry.occurredAt desc, entry.id desc
          """)
  Page<OperationLog> searchAll(String keyword, Pageable pageable);

  /**
   * 部门管理员查看日志：只检索用户范围记录，且至少一个操作对象在发生时位于管理部门内；
   * 关键字匹配的对象名称也必须是范围内的对象，避免借关键字探测范围外的对象。
   *
   * @param departmentIds 管理范围内的部门 ID
   * @param keyword 关键字，空串表示不过滤
   * @param pageable 分页参数
   * @return 当前页的操作记录，按发生时间和 ID 倒序
   */
  @Query("""
          select entry from OperationLog entry
          where entry.scope = 'USER_SCOPE' and exists (
              select 1 from OperationLogTarget target
              where target.operationLogId = entry.id and target.departmentIdAtEvent in :departmentIds
                and (
          """ + MATCH_LOG_FIELDS + """
                     or locate(:keyword, target.targetName) > 0)
          )
          order by entry.occurredAt desc, entry.id desc
          """)
  Page<OperationLog> searchByDepartmentScope(
      List<Long> departmentIds, String keyword, Pageable pageable);
}
