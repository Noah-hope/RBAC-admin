package com.rbac.rbac1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** RBAC1 结构化（面向功能）后端的启动类：按 controller、service、mapper 分层组织全部 44 个接口。 */
@SpringBootApplication
public class RbacStructuredApplication {

  /**
   * 启动 Spring Boot 应用。
   *
   * @param args 命令行参数，可覆盖配置项
   */
  public static void main(String[] args) {
    SpringApplication.run(RbacStructuredApplication.class, args);
  }
}
