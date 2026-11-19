package com.rbac.rbac1.vo;

import java.time.Instant;
import java.util.List;

/**
 * 操作日志的响应体。
 *
 * @param id 日志 ID
 * @param occurredAt 操作时间
 * @param actorName 操作人姓名
 * @param action 操作类型
 * @param targetNames 操作对象名称
 * @param detail 操作内容
 * @param result 操作结果
 */
public record OperationLogVO(
    long id,
    Instant occurredAt,
    String actorName,
    String action,
    List<String> targetNames,
    String detail,
    String result) {}
