package com.rbac.rbac1ddd.application.auth;

import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.APPLICATION_MISMATCH;
import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.INVALID_REQUEST;

import com.rbac.rbac1ddd.domain.shared.DomainException;
import java.util.List;

/**
 * 认证通过的登录会话：当前操作者和令牌适用的应用。
 *
 * @param operator 当前操作者及其管理身份
 * @param audience 令牌受众，即登录时选择的应用编码
 */
public record AuthenticatedSession(Operator operator, List<String> audience) {

  /** 复制受众列表，防止外部修改。 */
  public AuthenticatedSession {
    audience = List.copyOf(audience);
  }

  /**
   * 确定本次请求的目标应用。
   *
   * <p>请求指定了应用时，令牌必须适用于该应用；未指定时，令牌必须只适用于一个应用，并以它为目标。
   *
   * @param requestedCode 请求指定的应用编码（X-Application-Code），可以为 null
   * @return 目标应用编码
   * @throws DomainException 未指定且令牌适用多个应用时为 INVALID_REQUEST；令牌不适用于目标应用时为
   *     APPLICATION_MISMATCH
   */
  public String applicationContext(String requestedCode) {
    String code = requestedCode;
    if (code == null) {
      if (audience.size() != 1) throw new DomainException(INVALID_REQUEST, "请指定目标应用。");
      code = audience.getFirst();
    }
    if (!audience.contains(code))
      throw new DomainException(APPLICATION_MISMATCH, "令牌不适用于该应用。");
    return code;
  }
}
