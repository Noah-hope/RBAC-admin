package com.rbac.rbac1ddd.application.shared;

import com.rbac.rbac1ddd.application.auth.Operator;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.Getter;

/**
 * 一条操作记录。
 *
 * <p>字段不设 final 并提供 getter，供 CloneFactory 拷贝到持久化对象；CloneFactory 不支持 final 字段和
 * record。只能通过 {@link #success} 和 {@link #failure} 创建，发生时间取 UTC。
 */
@Getter
public class OperationRecord {
  /** 全局可见范围：只有总管理员可见。 */
  public static final String GLOBAL = "GLOBAL";

  /** 用户范围：部门管理员按操作时的部门筛选可见。 */
  public static final String USER_SCOPE = "USER_SCOPE";

  /** 操作结果：成功。 */
  public static final String RESULT_SUCCESS = "SUCCESS";

  /** 操作结果：失败。 */
  public static final String RESULT_FAILURE = "FAILURE";

  private LocalDateTime occurredAt;
  private Long actorUserId;
  private String actorName;
  private String action;
  private String scope;
  private String detail;
  private String result;

  private OperationRecord(
      Operator actor, String action, String scope, String detail, String result) {
    this.occurredAt = LocalDateTime.now(ZoneOffset.UTC);
    this.actorUserId = actor.id();
    this.actorName = actor.name();
    this.action = action;
    this.scope = scope;
    this.detail = detail;
    this.result = result;
  }

  /**
   * 创建成功的操作记录。
   *
   * @param scope 可见范围，取 {@link #GLOBAL} 或 {@link #USER_SCOPE}
   */
  public static OperationRecord success(
      Operator actor, String action, String scope, String detail) {
    return new OperationRecord(actor, action, scope, detail, RESULT_SUCCESS);
  }

  /** 创建失败的操作记录，动作固定为「操作失败」，范围为全局。 */
  public static OperationRecord failure(Operator actor, String detail) {
    return new OperationRecord(actor, "操作失败", GLOBAL, detail, RESULT_FAILURE);
  }
}
