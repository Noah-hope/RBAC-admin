package com.rbac.rbac1ddd.interfaces.startup;

import com.rbac.rbac1ddd.application.auth.BootstrapApplicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动入口：应用启动完成后确保存在总管理员。
 *
 * <p>配置项名称与 rbac1-structured 相同（rbac.bootstrap.username / rbac.bootstrap.password，
 * 对应环境变量 RBAC1_ADMIN_USERNAME / RBAC1_ADMIN_PASSWORD）。
 */
@Component
class TotalAdminBootstrap implements ApplicationRunner {

  private final BootstrapApplicationService bootstrap;
  private final String username;
  private final String password;

  /**
   * 注入初始化服务和配置的总管理员账号。
   *
   * @param bootstrap 初始化应用服务
   * @param username 首次启动创建的总管理员账号
   * @param password 首次启动创建的总管理员初始密码
   */
  TotalAdminBootstrap(
      BootstrapApplicationService bootstrap,
      @Value("${rbac.bootstrap.username}")
      String username,
      @Value("${rbac.bootstrap.password}")
      String password) {
    this.bootstrap = bootstrap;
    this.username = username;
    this.password = password;
  }

  /** 还没有总管理员时按配置创建；配置不完整时启动失败。 */
  @Override
  public void run(ApplicationArguments args) {
    bootstrap.ensureTotalAdmin(username, password);
  }
}
