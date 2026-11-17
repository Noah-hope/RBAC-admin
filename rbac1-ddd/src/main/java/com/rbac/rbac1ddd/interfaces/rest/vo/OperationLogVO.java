package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.audit.OperationLogResult;
import java.time.Instant;
import java.util.List;

/**
 * 操作记录的响应体（OperationLog），字段与 rbac1-structured 的 OperationLogView 相同。
 *
 * @param id 记录 ID
 * @param occurredAt 发生时间
 * @param actorName 操作人姓名
 * @param action 操作名称
 * @param targetNames 可见的操作对象名称
 * @param detail 操作内容
 * @param result 操作结果：SUCCESS 或 FAILURE
 */
public record OperationLogVO(
    long id,
    Instant occurredAt,
    String actorName,
    String action,
    List<String> targetNames,
    String detail,
    String result) {

  /** 由应用层结果逐字段转换。 */
  public static OperationLogVO from(OperationLogResult result) {
    return new OperationLogVO(
        result.id(),
        result.occurredAt(),
        result.actorName(),
        result.action(),
        result.targetNames(),
        result.detail(),
        result.result());
  }
}
