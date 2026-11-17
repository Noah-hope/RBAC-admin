package com.rbac.rbac1ddd.infrastructure.security;

import com.rbac.rbac1ddd.application.auth.PasswordHasher;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 密码摘要端口的 Argon2 实现。
 *
 * <p>参数与 rbac1-structured 的 AuthenticationConfig 完全相同（盐 16 字节、摘要 32 字节、并行度 1、
 * 内存 19 MiB、迭代 2 次），两个后端共用数据库时才能互相验证密码。
 */
@Component
class Argon2PasswordHasher implements PasswordHasher {

  private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);

  /** 用随机盐计算 Argon2id 摘要。 */
  @Override
  public String hash(String rawPassword) {
    return encoder.encode(rawPassword);
  }

  /** 校验明文密码；摘要格式不正确时返回 false。 */
  @Override
  public boolean matches(String rawPassword, String passwordHash) {
    return encoder.matches(rawPassword, passwordHash);
  }
}
