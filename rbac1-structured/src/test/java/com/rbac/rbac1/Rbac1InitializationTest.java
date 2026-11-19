package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * 完整初始化脚本 V1__init_rbac1.sql 的测试：在空的 H2（MySQL 模式）库中执行脚本，
 * 检查表结构、系统预置数据和继承相关的数据库约束。
 */
class Rbac1InitializationTest {

  @Test
  @DisplayName("RBAC0：初始化脚本在空库中建出全部 17 张表和必需的系统数据，总管理员拥有全部权限")
  void initializesFinalSchemaAndRequiredSystemDataInAnEmptyDatabase() throws Exception {
    try (Connection connection =
        DriverManager.getConnection("jdbc:h2:mem:rbac1-init;MODE=MySQL", "sa", "")) {
      initialize(connection);
      assertThat(
              number(
                  connection,
                  "select count(*) from information_schema.tables where table_schema='PUBLIC'"))
          .isEqualTo(17);
      assertThat(number(connection, "select count(*) from sys_role")).isEqualTo(2);
      assertThat(number(connection, "select count(*) from sys_user")).isZero();
      assertThat(strings(connection, "select code from sys_application"))
          .containsExactly("permission-center");
      assertThat(
              strings(
                  connection,
                  "select column_name from information_schema.columns where"
                      + " table_name='SYS_APPLICATION'"))
          .contains("CODE")
          .doesNotContain("TOKEN_HASH");
      assertThat(number(connection, "select count(*) from sys_api")).isEqualTo(38);
      assertThat(
              number(
                  connection,
                  "select count(*) from sys_role_permission g join sys_role r on r.id=g.role_id"
                      + " where r.role_type='TOTAL_ADMIN'"))
          .isEqualTo(number(connection, "select count(*) from sys_permission"));
    }
  }

  @Test
  @DisplayName("RBAC0：初始化的接口保护覆盖需要权限的接口，两种管理员都能分配角色，部门管理员不能新增或修改部门")
  void initializesApiProtectionAndAssignmentRightsForBothAdministrators() throws Exception {
    try (Connection connection =
        DriverManager.getConnection("jdbc:h2:mem:rbac1-protection;MODE=MySQL", "sa", "")) {
      initialize(connection);
      assertThat(
              strings(
                  connection,
                  "select a.operation_id from sys_api a left join sys_api_permission p on"
                      + " p.api_id=a.id where p.api_id is null"))
          .containsExactlyInAnyOrder(
              "getMyPermissions",
              "getRequestablePermissions",
              "listPermissionRequests",
              "createPermissionRequest",
              "getPermissionRequest",
              "withdrawPermissionRequest");
      assertThat(
              strings(
                  connection,
                  "select p.code from sys_permission p join sys_api_permission l on"
                      + " l.permission_id=p.id join sys_api a on a.id=l.api_id where"
                      + " a.operation_id='replaceUserBusinessRoles'"))
          .containsExactly("assignment:manage");
      assertThat(
              strings(
                  connection,
                  "select r.role_type from sys_role r join sys_role_permission g on r.id=g.role_id"
                      + " join sys_permission p on p.id=g.permission_id where"
                      + " p.code='assignment:manage'"))
          .containsExactlyInAnyOrder("TOTAL_ADMIN", "DEPARTMENT_ADMIN");
      assertThat(
              number(
                  connection,
                  "select count(*) from sys_role_permission g join sys_role r on r.id=g.role_id"
                      + " join sys_permission p on p.id=g.permission_id where"
                      + " r.role_type='DEPARTMENT_ADMIN' and p.code in"
                      + " ('department:create','department:update')"))
          .isZero();
    }
  }

  @Test
  @DisplayName("验收7：数据库约束阻止删除仍有继承关系的角色和重复继承，角色删除后申请的父角色快照仍保留")
  void restrictsLiveInheritanceButRetainsApplicationParentSnapshots() throws Exception {
    try (Connection connection =
        DriverManager.getConnection("jdbc:h2:mem:rbac1-constraints;MODE=MySQL", "sa", "")) {
      initialize(connection);
      Statement sql = connection.createStatement();
      sql.execute(
          "insert into sys_role(id,name,role_type)"
              + " values(100,'父角色','BUSINESS'),(101,'子角色','BUSINESS')");
      sql.execute(
          "insert into sys_user(id,username,name,password_hash) values(1,'manager','管理员','hash')");
      sql.execute(
          "insert into sys_role_create_request(id,applicant_user_id,role_name,status,created_at)"
              + " values(10,1,'申请角色','PENDING',CURRENT_TIMESTAMP)");
      sql.execute("insert into sys_role_inheritance values(101,100)");
      sql.execute("insert into sys_role_create_request_inheritance values(10,100,'父角色')");
      assertThatThrownBy(() -> sql.execute("delete from sys_role where id=100"))
          .isInstanceOf(SQLException.class);
      assertThatThrownBy(() -> sql.execute("insert into sys_role_inheritance values(101,100)"))
          .isInstanceOf(SQLException.class);
      sql.execute("delete from sys_role_inheritance");
      sql.execute("delete from sys_role where id=100");
      assertThat(
              strings(
                  connection,
                  "select inherited_role_name from sys_role_create_request_inheritance where"
                      + " request_id=10"))
          .containsExactly("父角色");
      assertThatThrownBy(
              () ->
                  sql.execute(
                      "insert into sys_role_create_request_inheritance values(99,101,'无申请')"))
          .isInstanceOf(SQLException.class);
    }
  }

  /** 在给定连接上执行完整初始化迁移脚本，脚本缺失时测试失败。 */
  private void initialize(Connection connection) throws Exception {
    ClassPathResource resource = new ClassPathResource("db/migration/V1__init_rbac1.sql");
    assertThat(resource.exists()).as("完整初始化迁移必须可执行").isTrue();
    String script = resource.getContentAsString(StandardCharsets.UTF_8);
    // H2 不支持 MySQL 的列级字符集和排序规则，仅在测试执行前移除这两种声明。
    script =
        script
            .replace(" CHARACTER SET ascii COLLATE ascii_bin", "")
            .replace(" COLLATE utf8mb4_0900_bin", "");
    new ResourceDatabasePopulator(new ByteArrayResource(script.getBytes(StandardCharsets.UTF_8)))
        .populate(connection);
  }

  /** 执行返回单个数值的查询。 */
  private long number(Connection connection, String query) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(query)) {
      result.next();
      return result.getLong(1);
    }
  }

  /** 执行查询并按顺序返回第一列的字符串值。 */
  private List<String> strings(Connection connection, String query) throws SQLException {
    try (Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(query)) {
      List<String> values = new ArrayList<>();
      while (result.next()) {
        values.add(result.getString(1));
      }
      return values;
    }
  }
}
