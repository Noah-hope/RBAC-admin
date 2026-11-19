package com.rbac.rbac1.service;

import static com.rbac.rbac1.service.DepartmentService.isTotalAdmin;

import com.rbac.rbac1.entity.OperationLog;
import com.rbac.rbac1.entity.OperationLogTarget;
import com.rbac.rbac1.mapper.OperationLogMapper;
import com.rbac.rbac1.mapper.OperationLogTargetMapper;
import com.rbac.rbac1.vo.OperationLogVO;
import com.rbac.rbac1.vo.PageVO;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 操作记录查询：总管理员看全部记录，部门管理员只看涉及其管理部门用户的记录。
 */
@Service
@RequiredArgsConstructor
public class OperationLogQueryService {

  private final DepartmentService departments;

  private final OperationLogMapper logs;

  private final OperationLogTargetMapper targets;

  /**
   * 分页查询操作记录。
   *
   * <p>部门管理员只能看到范围为 USER_SCOPE 且对象在事件发生时属于其管理部门的记录，
   * 并且每条记录只显示这些可见对象的名称。
   *
   * @param actor 当前操作人
   * @param page 页码，从 1 开始
   * @param pageSize 每页条数
   * @param keyword 匹配操作人、动作、详情或对象名称，空串表示不过滤
   * @return 按发生时间倒序的一页记录
   */
  @Transactional(readOnly = true)
  public PageVO<OperationLogVO> list(SessionUser actor, int page, int pageSize, String keyword) {
    boolean totalAdmin = isTotalAdmin(actor);
    List<Long> departmentIds =
        totalAdmin
            ? List.<Long>of()
            : departments.managedDepartments(actor, null).stream()
                .map(department -> department.getId())
                .toList();
    PageRequest pageable = PageRequest.of(page - 1, pageSize);
    Page<OperationLog> matches =
        totalAdmin
            ? logs.searchAll(keyword, pageable)
            : logs.searchByDepartmentScope(departmentIds, keyword, pageable);
    List<Long> logIds = matches.stream().map(log -> log.getId()).toList();
    if (logIds.isEmpty()) {
      return new PageVO<>(List.of(), page, pageSize, matches.getTotalElements());
    }

    List<OperationLogTarget> visibleTargets =
        totalAdmin
            ? targets.findByOperationLogIdInOrderByTargetNoAsc(logIds)
            : targets.findByOperationLogIdInAndDepartmentIdAtEventInOrderByTargetNoAsc(
                logIds, departmentIds);
    Map<Long, List<String>> namesByLog =
        visibleTargets.stream()
            .collect(
                Collectors.groupingBy(
                    target -> target.getOperationLogId(),
                    Collectors.mapping(target -> target.getTargetName(), Collectors.toList())));
    List<OperationLogVO> items =
        matches.stream()
            .map(
                log ->
                    new OperationLogVO(
                        log.getId(),
                        log.getOccurredAt().toInstant(ZoneOffset.UTC),
                        log.getActorName(),
                        log.getAction(),
                        namesByLog.getOrDefault(log.getId(), List.of()),
                        log.getDetail(),
                        log.getResult()))
            .toList();
    return new PageVO<>(items, page, pageSize, matches.getTotalElements());
  }
}
