package com.rbac.rbac1ddd.application.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 登录限流：同一远程地址在一个 1 分钟窗口内失败 5 次后，窗口结束前拒绝登录，与 rbac1-structured 相同。
 *
 * <p>计数保存在本进程内存中；窗口到期时清空全部地址的计数。多个请求线程共享计数，方法加同步。
 */
@Component
class LoginAttempts {

  /** 一个窗口内允许的失败次数。 */
  private static final int MAX_FAILURES = 5;

  /** 计数窗口长度。 */
  private static final Duration WINDOW = Duration.ofMinutes(1);

  private final Map<String, Integer> failures = new HashMap<>();
  private Instant windowEndsAt = Instant.MIN;

  /**
   * 要求该地址当前允许登录。
   *
   * @param address 远程地址
   * @throws AuthenticationFailure 失败次数已达上限时为 TOO_MANY_REQUESTS，并带上距窗口结束的秒数（至少 1 秒）
   */
  synchronized void requireAvailable(String address) {
    Instant now = Instant.now();
    resetExpiredWindow(now);
    if (failures.getOrDefault(address, 0) >= MAX_FAILURES) {
      long retryAfter = Math.max(1, Duration.between(now, windowEndsAt).toSeconds());
      throw new AuthenticationFailure(AuthenticationFailure.Reason.TOO_MANY_REQUESTS, retryAfter);
    }
  }

  /** 记录一次账号或密码错误。 */
  synchronized void recordFailure(String address) {
    resetExpiredWindow(Instant.now());
    failures.merge(address, 1, Integer::sum);
  }

  /** 登录成功后清除该地址的失败计数。 */
  synchronized void recordSuccess(String address) {
    failures.remove(address);
  }

  /** 当前窗口已结束时清空计数并开始新窗口。 */
  private void resetExpiredWindow(Instant now) {
    if (!now.isBefore(windowEndsAt)) {
      failures.clear();
      windowEndsAt = now.plus(WINDOW);
    }
  }
}
