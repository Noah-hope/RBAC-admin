package com.rbac.rbac1ddd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * RBAC1 面向对象（DDD）后端的启动类。
 *
 * <p>与 rbac1-structured 共用同一个数据库和同一份接口契约，默认端口 8082。
 */
@SpringBootApplication
public class Rbac1DddApplication {

  /**
   * 启动 Spring Boot 应用。
   *
   * @param args 命令行参数
   */
  public static void main(String[] args) {
    SpringApplication.run(Rbac1DddApplication.class, args);
  }
}
