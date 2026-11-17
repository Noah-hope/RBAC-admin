package com.rbac.rbac1ddd.application.audit;

import java.time.Instant;
import java.util.List;

/**
 * 一条可见的操作记录（OperationLog）。
 *
 * @param id 记录 ID
 * @param occurredAt 发生时间
 * @param actorName 操作发生时操作人的姓名
 * @param action 操作名称
 * @param targetNames 查看者可见的操作对象名称，按对象序号排列
 * @param detail 操作内容
 * @param result 操作结果：SUCCESS 或 FAILURE
 */
public record OperationLogResult(
    long id,
    Instant occurredAt,
    String actorName,
    String action,
    List<String> targetNames,
    String detail,
    String result) {}
