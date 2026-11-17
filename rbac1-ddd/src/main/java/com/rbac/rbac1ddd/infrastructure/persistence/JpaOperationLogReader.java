package com.rbac.rbac1ddd.infrastructure.persistence;

import com.rbac.rbac1ddd.application.audit.OperationLogReader;
import com.rbac.rbac1ddd.application.audit.OperationLogResult;
import com.rbac.rbac1ddd.application.shared.OperationRecord;
import com.rbac.rbac1ddd.application.shared.PageResult;
import com.rbac.rbac1ddd.infrastructure.persistence.dao.OperationLogDao;
import com.rbac.rbac1ddd.infrastructure.persistence.dao.OperationLogTargetDao;
import com.rbac.rbac1ddd.infrastructure.persistence.po.OperationLogPo;
import com.rbac.rbac1ddd.infrastructure.persistence.po.OperationLogTargetPo;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/**
 * 操作记录读模型的 JPA 实现：先分页查记录，再一次读取当前页记录的可见对象名称。
 *
 * <p>发生时间按 UTC 保存，读取时转换为 Instant。
 */
@Repository
@RequiredArgsConstructor
class JpaOperationLogReader implements OperationLogReader {

  private final OperationLogDao logs;
  private final OperationLogTargetDao targets;

  /** 搜索全部记录，列出每条记录的全部对象。 */
  @Override
  public PageResult<OperationLogResult> searchAll(String keyword, int page, int pageSize) {
    Page<OperationLogPo> found = logs.searchAll(keyword, PageRequest.of(page - 1, pageSize));
    return assemble(found, page, pageSize, targets::findByOperationLogIdInOrderByTargetNoAsc);
  }

  /** 搜索部门范围内的用户范围记录，只列出操作时在这些部门内的对象。 */
  @Override
  public PageResult<OperationLogResult> searchInDepartments(
      Set<Long> departmentIds, String keyword, int page, int pageSize) {
    Page<OperationLogPo> found =
        logs.searchByDepartments(
            OperationRecord.USER_SCOPE, departmentIds, keyword, PageRequest.of(page - 1, pageSize));
    return assemble(
        found,
        page,
        pageSize,
        logIds ->
            targets.findByOperationLogIdInAndDepartmentIdAtEventInOrderByTargetNoAsc(
                logIds, departmentIds));
  }

  /**
   * 为当前页记录配上可见对象名称。
   *
   * @param found 当前页记录
   * @param visibleTargets 按记录 ID 读取可见对象的方法
   */
  private static PageResult<OperationLogResult> assemble(
      Page<OperationLogPo> found,
      int page,
      int pageSize,
      Function<List<Long>, List<OperationLogTargetPo>> visibleTargets) {
    List<Long> logIds = found.getContent().stream().map(OperationLogPo::getId).toList();
    if (logIds.isEmpty()) {
      return new PageResult<>(List.of(), page, pageSize, found.getTotalElements());
    }
    Map<Long, List<String>> namesByLog =
        visibleTargets.apply(logIds).stream()
            .collect(
                Collectors.groupingBy(
                    OperationLogTargetPo::getOperationLogId,
                    Collectors.mapping(OperationLogTargetPo::getTargetName, Collectors.toList())));
    List<OperationLogResult> items =
        found.getContent().stream()
            .map(
                entry ->
                    new OperationLogResult(
                        entry.getId(),
                        entry.getOccurredAt().toInstant(ZoneOffset.UTC),
                        entry.getActorName(),
                        entry.getAction(),
                        namesByLog.getOrDefault(entry.getId(), List.of()),
                        entry.getDetail(),
                        entry.getResult()))
            .toList();
    return new PageResult<>(items, page, pageSize, found.getTotalElements());
  }
}
