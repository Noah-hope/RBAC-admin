package com.rbac.rbac1.service;

import static com.rbac.rbac1.service.AuthenticationFailure.Reason.ACCOUNT_DISABLED;
import static com.rbac.rbac1.service.AuthenticationFailure.Reason.UNAUTHENTICATED;

import com.rbac.rbac1.config.JwtTokens;
import com.rbac.rbac1.config.TokenHash;
import com.rbac.rbac1.dto.LoginDTO;
import com.rbac.rbac1.entity.LoginSession;
import com.rbac.rbac1.entity.Role;
import com.rbac.rbac1.entity.UserAccount;
import com.rbac.rbac1.mapper.ApplicationMapper;
import com.rbac.rbac1.mapper.RoleMapper;
import com.rbac.rbac1.mapper.SessionMapper;
import com.rbac.rbac1.mapper.UserMapper;
import com.rbac.rbac1.vo.LoginVO;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登录会话管理：校验账号密码签发 JWT，并在服务端保存令牌摘要，使退出登录和停用账号能立即让令牌失效。
 */
@Service
@RequiredArgsConstructor
public class SessionService {

  private static final Duration SESSION_LIFETIME = Duration.ofHours(2);

  private final UserMapper users;

  private final RoleMapper roles;

  private final SessionMapper sessions;

  private final PasswordEncoder passwords;

  private final LoginAttempts loginAttempts;

  private final JwtTokens tokens;

  private final ApplicationMapper applications;

  /**
   * 登录并签发访问令牌。
   *
   * <p>先校验账号密码再检查账号状态，避免向未通过密码校验的请求暴露账号是否停用；
   * 只有密码错误计入失败次数。
   *
   * @param request 登录请求，systems 是令牌可用于的应用编码
   * @param address 客户端来源地址，用于限制失败次数
   * @return 访问令牌及有效期
   * @throws AuthenticationFailure 账号密码错误、账号停用或尝试过于频繁
   * @throws ManagementFailure 选择的应用重复或未登记时为 INVALID_REQUEST
   */
  @Transactional
  public LoginVO login(LoginDTO request, String address) {
    loginAttempts.requireAvailable(address);
    UserAccount user = users.findByUsername(request.username()).orElse(null);
    if (user == null || !passwords.matches(request.password(), user.getPasswordHash())) {
      loginAttempts.recordFailure(address);
      throw new AuthenticationFailure(UNAUTHENTICATED);
    }
    if (!UserAccount.ACTIVE.equals(user.getStatus())) {
      throw new AuthenticationFailure(ACCOUNT_DISABLED);
    }

    if (request.systems().stream().distinct().count() != request.systems().size()
        || request.systems().stream().anyMatch(code -> applications.findByCode(code).isEmpty())) {
      throw new ManagementFailure(ManagementFailure.Reason.INVALID_REQUEST, "请选择已登记的应用。");
    }
    Instant expiresAt = Instant.now().plus(SESSION_LIFETIME);
    String token = tokens.issue(user.getId(), request.systems(), expiresAt);
    LoginSession session = new LoginSession();
    session.setTokenHash(TokenHash.sha256(token));
    session.setUserId(user.getId());
    session.setExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
    sessions.save(session);
    loginAttempts.recordSuccess(address);
    return new LoginVO(token, "Bearer", SESSION_LIFETIME.toSeconds());
  }

  /**
   * 校验令牌签名、服务端会话和账号状态，得到当前操作人。
   *
   * @param token Bearer 令牌
   * @return 操作人和令牌适用的应用编码
   * @throws AuthenticationFailure 令牌无效、会话已失效或账号停用时为 UNAUTHENTICATED
   */
  @Transactional(readOnly = true)
  public AuthenticatedSession authenticate(String token) {
    Jwt jwt = tokens.verify(token);
    UserAccount user = requireActiveSession(TokenHash.sha256(token));
    if (!Long.toString(user.getId()).equals(jwt.getSubject())) {
      throw new AuthenticationFailure(UNAUTHENTICATED);
    }
    return new AuthenticatedSession(identity(user), jwt.getAudience());
  }

  /**
   * 退出登录，删除该令牌对应的服务端会话。
   *
   * @param token Bearer 令牌
   * @throws AuthenticationFailure 会话不存在（已退出或已失效）时为 UNAUTHENTICATED
   */
  @Transactional
  public void logout(String token) {
    byte[] tokenHash = TokenHash.sha256(token);
    if (sessions.revoke(tokenHash) == 0) {
      throw new AuthenticationFailure(UNAUTHENTICATED);
    }
  }

  /**
   * 确定本次请求的目标应用。
   *
   * <p>未指定应用时，令牌必须只适用于一个应用；指定的应用必须在令牌的适用范围内。
   *
   * @param audience 令牌适用的应用编码
   * @param applicationCode 请求指定的应用编码，可为 {@code null}
   * @return 目标应用编码
   * @throws ManagementFailure 无法确定应用时为 INVALID_REQUEST，令牌不适用时为 APPLICATION_MISMATCH
   */
  public String applicationContext(List<String> audience, String applicationCode) {
    String code = applicationCode;
    if (code == null) {
      if (audience.size() != 1) {
        throw new ManagementFailure(ManagementFailure.Reason.INVALID_REQUEST, "请指定目标应用。");
      }
      code = audience.getFirst();
    }
    if (!audience.contains(code)) {
      throw new ManagementFailure(ManagementFailure.Reason.APPLICATION_MISMATCH, "令牌不适用于该应用。");
    }
    return code;
  }

  /**
   * 认证通过的会话。
   *
   * @param actor 当前操作人
   * @param audience 令牌适用的应用编码
   */
  public record AuthenticatedSession(SessionUser actor, List<String> audience) {}

  /** 查找未过期的服务端会话，并要求会话所属账号仍为启用状态。 */
  private UserAccount requireActiveSession(byte[] tokenHash) {
    LoginSession session =
        sessions
            .findById(tokenHash)
            .filter(value -> value.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)))
            .orElseThrow(() -> new AuthenticationFailure(UNAUTHENTICATED));
    return users
        .findById(session.getUserId())
        .filter(user -> UserAccount.ACTIVE.equals(user.getStatus()))
        .orElseThrow(() -> new AuthenticationFailure(UNAUTHENTICATED));
  }

  /** 组装操作人身份；同时拥有两种管理角色时以总管理员为准，没有管理角色为 NONE。 */
  private SessionUser identity(UserAccount user) {
    List<String> managementRoles = roles.findManagementRoles(user.getId());
    String managementRole =
        managementRoles.contains(Role.TOTAL_ADMIN)
            ? Role.TOTAL_ADMIN
            : managementRoles.contains(Role.DEPARTMENT_ADMIN) ? Role.DEPARTMENT_ADMIN : "NONE";
    return new SessionUser(
        user.getId(), user.getUsername(), user.getName(), user.getDepartmentId(), managementRole);
  }
}
