package com.rbac.rbac1.service;

import static com.rbac.rbac1.service.AuthenticationFailure.Reason.TOO_MANY_REQUESTS;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 按来源地址统计登录失败次数，同一时间窗口内失败过多时拒绝继续尝试。
 *
 * <p>所有地址共用一个固定窗口，窗口到期后整体清零；计数只保存在内存中，重启后重新计算。
 */
@Component
class LoginAttempts {

  private static final int MAX_FAILURES = 5;

  private static final Duration WINDOW = Duration.ofMinutes(1);

  private final Map<String, Integer> failures = new HashMap<>();

  private Instant windowEndsAt = Instant.MIN;

  /**
   * 校验该地址还能继续尝试登录。
   *
   * @param address 客户端来源地址
   * @throws AuthenticationFailure 失败次数已达上限时抛出，并给出剩余等待秒数
   */
  synchronized void requireAvailable(String address) {
    Instant now = Instant.now();
    resetExpiredWindow(now);
    if (failures.getOrDefault(address, 0) >= MAX_FAILURES) {
      long retryAfter = Math.max(1, Duration.between(now, windowEndsAt).toSeconds());
      throw new AuthenticationFailure(TOO_MANY_REQUESTS, retryAfter);
    }
  }

  /**
   * 记录一次登录失败。
   *
   * @param address 客户端来源地址
   */
  synchronized void recordFailure(String address) {
    resetExpiredWindow(Instant.now());
    failures.merge(address, 1, Integer::sum);
  }

  /**
   * 登录成功后清除该地址的失败计数。
   *
   * @param address 客户端来源地址
   */
  synchronized void recordSuccess(String address) {
    failures.remove(address);
  }

  /** 窗口到期时清空全部计数并开启新窗口。 */
  private void resetExpiredWindow(Instant now) {
    if (!now.isBefore(windowEndsAt)) {
      failures.clear();
      windowEndsAt = now.plus(WINDOW);
    }
  }
}
