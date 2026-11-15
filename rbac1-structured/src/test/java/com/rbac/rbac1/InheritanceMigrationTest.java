package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

class InheritanceMigrationTest {
  @Test
  void migrationRestrictsLiveInheritanceButAllowsDeletingSnapshotParents() throws Exception {
    try (var connection =
        DriverManager.getConnection("jdbc:h2:mem:migration;MODE=MySQL", "sa", "")) {
      var sql = connection.createStatement();
      sql.execute("create table sys_role(id bigint primary key)");
      sql.execute("create table sys_role_create_request(id bigint primary key)");
      new ResourceDatabasePopulator(new ClassPathResource("db/migration/V5__role_inheritance.sql"))
          .populate(connection);
      sql.execute("insert into sys_role values(1),(2)");
      sql.execute("insert into sys_role_create_request values(10)");
      sql.execute("insert into sys_role_inheritance values(2,1)");
      sql.execute("insert into sys_role_create_request_inheritance values(10,1,'原父角色')");
      assertThatThrownBy(() -> sql.execute("delete from sys_role where id=1"))
          .isInstanceOf(SQLException.class);
      assertThatThrownBy(() -> sql.execute("insert into sys_role_inheritance values(2,1)"))
          .isInstanceOf(SQLException.class);
      sql.execute("delete from sys_role_inheritance");
      sql.execute("delete from sys_role where id=1");
      try (var result =
          sql.executeQuery(
              "select inherited_role_name from sys_role_create_request_inheritance where"
                  + " request_id=10")) {
        assertThat(result.next()).isTrue();
        assertThat(result.getString(1)).isEqualTo("原父角色");
      }
      assertThatThrownBy(
              () ->
                  sql.execute("insert into sys_role_create_request_inheritance values(99,2,'无申请')"))
          .isInstanceOf(SQLException.class);
    }
  }
}
