package com.rbac.rbac1.service;

/**
 * 管理操作的业务错误：携带错误原因和可直接展示的中文提示，由统一异常处理转换为 problem+json。
 */
public final class ManagementFailure extends RuntimeException {

  /**
   * 业务错误原因，决定 HTTP 状态码和对外错误码。
   */
  public enum Reason {
    /** 没有执行该操作的权限。 */
    FORBIDDEN,
    /** 目标对象不存在。 */
    NOT_FOUND,
    /** 与当前数据状态冲突。 */
    CONFLICT,
    /** 请求内容不符合业务规则。 */
    INVALID_REQUEST,
    /** 目标超出部门管理员的管理范围。 */
    OUTSIDE_MANAGEMENT_SCOPE,
    /** 引用的对象不属于路径中的应用。 */
    APPLICATION_MISMATCH,

    /** 违反唯一约束：对外与 CONFLICT 使用同一错误码，只是标题为「数据已存在」。 */
    DUPLICATE;

    /**
     * 对外错误码，同时写入失败操作记录。
     *
     * @return DUPLICATE 返回 CONFLICT，其余返回自身名称
     */
    public String code() {
      return this == DUPLICATE ? CONFLICT.name() : name();
    }
  }

  private final Reason reason;

  /**
   * 创建业务错误。
   *
   * @param reason 错误原因
   * @param detail 返回给前端的中文提示
   */
  public ManagementFailure(Reason reason, String detail) {
    super(detail);
    this.reason = reason;
  }

  /**
   * 错误原因。
   *
   * @return 构造时给出的原因
   */
  public Reason reason() {
    return reason;
  }
}
