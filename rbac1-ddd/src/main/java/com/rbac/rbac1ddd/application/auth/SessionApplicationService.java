package com.rbac.rbac1ddd.application.auth;

import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.INVALID_REQUEST;

import com.rbac.rbac1ddd.domain.app.BusinessApplicationRepository;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.user.User;
import com.rbac.rbac1ddd.domain.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用服务：统一登录、退出和签名公钥。
 *
 * <p>本系统是统一身份认证的提供方：验证账号密码后签发 RS256 令牌，会话表只保存令牌摘要；
 * 退出只撤销当前令牌对应的会话。
 */
@Service
@RequiredArgsConstructor
public class SessionApplicationService {

  /** 令牌和会话的有效期。 */
  private static final Duration SESSION_LIFETIME = Duration.ofHours(2);

  /** 返回给客户端的令牌类型。 */
  private static final String TOKEN_TYPE = "Bearer";

  private final UserRepository users;
  private final BusinessApplicationRepository applications;
  private final SessionStore sessions;
  private final PasswordHasher passwords;
  private final TokenIssuer tokens;
  private final LoginAttempts loginAttempts;

  /**
   * 验证账号密码并签发访问令牌。
   *
   * <p>检查顺序与 rbac1-structured 相同：先检查登录限流，再验证账号密码（错误时计入失败次数），
   * 然后要求账号启用，最后要求所选应用都已登记且不重复。
   *
   * @param command 登录输入
   * @param address 远程地址，用于登录限流
   * @return 访问令牌及其有效期
   */
  @Transactional
  public LoginResult login(LoginCommand command, String address) {
    loginAttempts.requireAvailable(address);
    User user = users.findByUsername(command.username()).orElse(null);
    if (user == null || !passwords.matches(command.password(), user.getPasswordHash())) {
      loginAttempts.recordFailure(address);
      throw new AuthenticationFailure();
    }
    if (!user.isActive()) {
      throw new AuthenticationFailure(AuthenticationFailure.Reason.ACCOUNT_DISABLED);
    }
    if (new HashSet<>(command.systems()).size() != command.systems().size()
        || command.systems().stream()
            .anyMatch(code -> applications.findByCode(code).isEmpty())) {
      throw new DomainException(INVALID_REQUEST, "请选择已登记的应用。");
    }
    Instant expiresAt = Instant.now().plus(SESSION_LIFETIME);
    String token = tokens.issue(user.getId(), command.systems(), expiresAt);
    sessions.open(token, user.getId(), expiresAt);
    loginAttempts.recordSuccess(address);
    return new LoginResult(token, TOKEN_TYPE, SESSION_LIFETIME.toSeconds());
  }

  /**
   * 退出：只撤销当前令牌对应的会话，同一用户的其他令牌继续有效。
   *
   * @param token 当前请求的令牌
   * @throws AuthenticationFailure 会话不存在时
   */
  @Transactional
  public void logout(String token) {
    if (!sessions.revoke(token)) {
      throw new AuthenticationFailure();
    }
  }

  /** 返回令牌签名公钥，供接入系统自行校验令牌签名。 */
  public SigningKeysResult signingKeys() {
    return new SigningKeysResult(tokens.publicKeys());
  }
}
