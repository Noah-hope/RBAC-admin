package com.rbac.rbac1.service;

import com.rbac.rbac1.entity.Role;
import com.rbac.rbac1.entity.UserAccount;
import com.rbac.rbac1.entity.UserRole;
import com.rbac.rbac1.mapper.RoleMapper;
import com.rbac.rbac1.mapper.UserMapper;
import com.rbac.rbac1.mapper.UserRoleMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 首次启动初始化：系统中还没有总管理员时，用配置的账号和密码创建总管理员。
 *
 * <p>已有总管理员时什么也不做，因此重复启动不会覆盖已修改的密码。
 */
@Service
public class BootstrapService implements ApplicationRunner {

  private final RoleMapper roles;

  private final UserMapper users;

  private final UserRoleMapper userRoles;

  private final PasswordEncoder passwords;

  private final String username;

  private final String password;

  /**
   * 注入依赖和初始化账号配置。
   *
   * @param roles 角色 Mapper
   * @param users 用户 Mapper
   * @param userRoles 用户角色关联 Mapper
   * @param passwords 密码编码器，保存 Argon2 摘要
   * @param username 初始总管理员账号，来自 rbac.bootstrap.username
   * @param password 初始总管理员密码，来自 rbac.bootstrap.password
   */
  public BootstrapService(
      RoleMapper roles,
      UserMapper users,
      UserRoleMapper userRoles,
      PasswordEncoder passwords,
      @Value("${rbac.bootstrap.username}")
      String username,
      @Value("${rbac.bootstrap.password}")
      String password) {
    this.roles = roles;
    this.users = users;
    this.userRoles = userRoles;
    this.passwords = passwords;
    this.username = username;
    this.password = password;
  }

  /**
   * 应用启动后检查并创建总管理员。
   *
   * @param args 启动参数，未使用
   * @throws IllegalStateException 需要创建总管理员但配置的账号或密码无效时阻止启动
   */
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    Role role = roles.findByRoleType(Role.TOTAL_ADMIN);
    if (userRoles.existsByRoleId(role.getId())) {
      return;
    }
    if (username.isBlank() || username.length() > 32 || password.isBlank()) {
      throw new IllegalStateException(
          "Set RBAC1_ADMIN_PASSWORD and a valid RBAC1_ADMIN_USERNAME for first startup");
    }
    UserAccount admin = new UserAccount();
    admin.setUsername(username);
    admin.setName("总管理员");
    admin.setPasswordHash(passwords.encode(password));
    admin.setStatus(UserAccount.ACTIVE);
    users.save(admin);

    UserRole assignment = new UserRole();
    assignment.setUserId(admin.getId());
    assignment.setRoleId(role.getId());
    userRoles.save(assignment);
  }
}
