package com.rbac.rbac1ddd.application.auth;

import lombok.Getter;

/** 身份认证失败：令牌无效、会话已失效、账号或密码错误、账号停用或登录过于频繁。接口层统一转换为错误响应。 */
@Getter
public class AuthenticationFailure extends RuntimeException {

  /** 认证失败的原因，接口层据此选择 HTTP 状态码和错误码。 */
  public enum Reason {
    /** 令牌、会话、账号或密码无效，返回 401。 */
    UNAUTHENTICATED,
    /** 账号已停用，返回 401。 */
    ACCOUNT_DISABLED,
    /** 登录失败次数过多，返回 429。 */
    TOO_MANY_REQUESTS
  }

  private final Reason reason;

  /** 建议客户端等待的秒数，只有登录限流时大于 0。 */
  private final long retryAfterSeconds;

  /** 创建原因为 UNAUTHENTICATED 的认证失败。 */
  public AuthenticationFailure() {
    this(Reason.UNAUTHENTICATED);
  }

  /**
   * 创建指定原因的认证失败。
   *
   * @param reason 失败原因
   */
  public AuthenticationFailure(Reason reason) {
    this(reason, 0);
  }

  /**
   * 创建指定原因并带重试等待时间的认证失败。
   *
   * @param reason 失败原因
   * @param retryAfterSeconds 建议客户端等待的秒数
   */
  public AuthenticationFailure(Reason reason, long retryAfterSeconds) {
    super(reason.name());
    this.reason = reason;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}
