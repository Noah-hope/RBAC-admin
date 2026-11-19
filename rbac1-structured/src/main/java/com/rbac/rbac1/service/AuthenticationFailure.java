package com.rbac.rbac1.service;

/**
 * 登录与会话校验失败，由统一异常处理转换为 401、403 或 429 响应。
 */
public final class AuthenticationFailure extends RuntimeException {

  /**
   * 认证失败的原因，决定对外的状态码和错误码。
   */
  public enum Reason {
    /** 账号或密码错误、令牌缺失或已失效。 */
    UNAUTHENTICATED,
    /** 账号已停用，不允许登录或继续使用会话。 */
    ACCOUNT_DISABLED,
    /** 同一来源地址在时间窗口内失败次数过多。 */
    TOO_MANY_REQUESTS
  }

  /** 失败原因。 */
  public final Reason reason;

  /** 建议客户端等待的秒数，只有 {@link Reason#TOO_MANY_REQUESTS} 时大于 0。 */
  public final long retryAfterSeconds;

  /**
   * 创建不需要等待重试的认证失败。
   *
   * @param reason 失败原因
   */
  public AuthenticationFailure(Reason reason) {
    this(reason, 0);
  }

  /**
   * 创建认证失败，并给出建议的重试等待时间。
   *
   * @param reason 失败原因
   * @param retryAfterSeconds 建议等待的秒数，写入 Retry-After 响应头
   */
  public AuthenticationFailure(Reason reason, long retryAfterSeconds) {
    super(reason.name());
    this.reason = reason;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}
