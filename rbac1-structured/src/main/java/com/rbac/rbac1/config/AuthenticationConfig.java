package com.rbac.rbac1.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 密码摘要配置：只保存 Argon2 摘要，参数与 DDD 后端一致，两个后端共用数据库时才能互相验证密码。 */
@Configuration(proxyBeanMethods = false)
public class AuthenticationConfig {

  /**
   * Argon2 密码编码器：盐 16 字节、摘要 32 字节、并行度 1、内存 19 MiB、迭代 2 次。
   *
   * @return 用于登录校验和新建账号的密码编码器
   */
  @Bean
  PasswordEncoder passwordEncoder() {
    return new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
  }
}
