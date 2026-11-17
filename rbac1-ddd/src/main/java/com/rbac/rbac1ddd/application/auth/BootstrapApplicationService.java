package com.rbac.rbac1ddd.application.auth;

import com.rbac.rbac1ddd.domain.role.Role;
import com.rbac.rbac1ddd.domain.role.RoleRepository;
import com.rbac.rbac1ddd.domain.role.RoleType;
import com.rbac.rbac1ddd.domain.user.User;
import com.rbac.rbac1ddd.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 应用服务：首次启动时创建总管理员，与 rbac1-structured 的启动初始化相同。 */
@Service
@RequiredArgsConstructor
public class BootstrapApplicationService {

  /** 登录账号的最大长度，与 sys_user.username 列一致。 */
  private static final int MAX_USERNAME_LENGTH = 32;

  private final RoleRepository roles;
  private final UserRepository users;
  private final PasswordHasher passwords;

  /**
   * 还没有任何用户持有总管理员角色时，用配置的账号和密码创建总管理员；已有时什么也不做。
   *
   * @param username 配置的登录账号（RBAC1_ADMIN_USERNAME）
   * @param password 配置的初始密码（RBAC1_ADMIN_PASSWORD）
   * @throws IllegalStateException 需要创建但账号为空、超长或密码为空时，阻止应用启动
   */
  @Transactional
  public void ensureTotalAdmin(String username, String password) {
    Role totalAdmin = roles.findByType(RoleType.TOTAL_ADMIN).orElseThrow();
    if (users.countHolding(totalAdmin.getId()) > 0) {
      return;
    }
    if (username.isBlank() || username.length() > MAX_USERNAME_LENGTH || password.isBlank()) {
      throw new IllegalStateException(
          "Set RBAC1_ADMIN_PASSWORD and a valid RBAC1_ADMIN_USERNAME for first startup");
    }
    users.save(User.firstTotalAdmin(username, passwords.hash(password), totalAdmin.getId()));
  }
}
