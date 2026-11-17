package com.rbac.rbac1ddd.application.auth;

/**
 * 密码摘要端口。实现必须与 rbac1-structured 使用相同的算法和参数，两个后端共用数据库时才能互相验证密码。
 */
public interface PasswordHasher {

  /**
   * 计算密码摘要，每次使用新的随机盐。
   *
   * @param rawPassword 明文密码
   * @return 可直接保存的摘要字符串
   */
  String hash(String rawPassword);

  /**
   * 判断明文密码与保存的摘要是否匹配。
   *
   * @param rawPassword 明文密码
   * @param passwordHash 保存的摘要
   * @return 匹配时为 true；摘要格式不正确时为 false
   */
  boolean matches(String rawPassword, String passwordHash);
}
