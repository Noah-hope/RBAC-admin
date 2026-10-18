package com.adgn.backend;

import com.adgn.backend.migration.MigrationApplication;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Explicit mysql-check profile only. Never use an existing development or shared database. */
class MigrationMySqlCheck {
    @Test
    void migratesAFreshIsolatedSchemaWithConstraintsAndCanBeRepeated() throws Exception {
        int port = Integer.parseInt(required("RBAC_ISOLATED_MYSQL_PORT"));
        if (port == 3306 || port < 1024 || port > 65535) {
            throw new IllegalStateException("Require an isolated, non-business local port");
        }
        Path expectedData = Path.of(required("RBAC_ISOLATED_MYSQL_DATADIR")).toRealPath();
        assertThat(expectedData.startsWith(Path.of(".tmp").toRealPath()))
                .as("server data must belong to the disposable workspace directory").isTrue();
        String serverUrl = "jdbc:mysql://127.0.0.1:" + port + "/";
        try (Connection admin = DriverManager.getConnection(serverUrl, "root", "")) {
            // Verify server identity before any CREATE/GRANT; fail closed on a wrong target.
            assertThat(Path.of(scalar(admin, "SELECT @@datadir")).toRealPath()).isEqualTo(expectedData);
            assertThat(scalar(admin, "SELECT VERSION()")).startsWith("8.4.");
            assertThat(scalar(admin, "SELECT @@log_bin")).isEqualTo("0");
            assertThat(scalar(admin, "SELECT @@foreign_key_checks")).isEqualTo("1");
            assertThat(scalar(admin, "SELECT @@sql_mode")).contains("STRICT_TRANS_TABLES");

            String nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            String schema = "rbacit" + nonce;
            String migrator = "rbacmit" + nonce;
            String runtime = "rbacait" + nonce;
            String password = UUID.randomUUID().toString(); // this disposable server only
            execute(admin, "CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin");
            try {
                execute(admin, "CREATE USER '" + migrator + "'@'localhost' IDENTIFIED BY '" + password + "'");
                execute(admin, "GRANT ALL PRIVILEGES ON `" + schema + "`.* TO '" + migrator + "'@'localhost'");
                String url = serverUrl + schema;
                assertThatCode(() -> migrate(url, migrator, password)).doesNotThrowAnyException();
                admin.setCatalog(schema);
                assertThat(scalar(admin, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()"))
                        .isEqualTo("44");
                assertThat(scalar(admin, "SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE()"))
                        .isEqualTo("79");
                assertThat(scalar(admin, "SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE()"))
                        .isEqualTo("41");
                assertThat(scalar(admin, "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version IN ('1','2','3')"))
                        .isEqualTo("3");
                assertThat(scalar(admin, "SELECT revision FROM mutation_guard WHERE id=1")).isEqualTo("0");
                assertSqlError(admin, "INSERT INTO mutation_guard(id,revision) VALUES(2,0)", 3819);
                assertSqlError(admin, "INSERT INTO mutation_guard(id,revision) VALUES(1,0)", 1062);
                assertSqlError(admin, "INSERT INTO department(id,company_id,code,name,version,created_at) "
                        + "VALUES(UUID(),UUID(),'FK_TEST','Fixture',1,UTC_TIMESTAMP(6))", 1452);
                assertThatCode(() -> migrate(url, migrator, password)).doesNotThrowAnyException();
                assertThat(scalar(admin, "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1")).isEqualTo("3");
                assertThat(scalar(admin, "SELECT COUNT(*) FROM mutation_guard")).isEqualTo("1");

                execute(admin, "CREATE USER '" + runtime + "'@'localhost' IDENTIFIED BY '" + password + "'");
                execute(admin, "GRANT SELECT,INSERT,UPDATE,DELETE ON `" + schema + "`.* TO '" + runtime + "'@'localhost'");
                try (Connection runtimeConnection = DriverManager.getConnection(url, runtime, password)) {
                    assertThat(scalar(runtimeConnection, "SELECT revision FROM mutation_guard WHERE id=1")).isEqualTo("0");
                    assertSqlError(runtimeConnection, "CREATE TABLE forbidden_ddl(id INT)", 1142);
                }
            } finally {
                // Names are generated here; server datadir was checked before fixture creation.
                execute(admin, "DROP DATABASE `" + schema + "`");
                execute(admin, "DROP USER IF EXISTS '" + migrator + "'@'localhost'");
                execute(admin, "DROP USER IF EXISTS '" + runtime + "'@'localhost'");
            }
        }
    }

    private static void migrate(String url, String username, String password) {
        try (var context = MigrationApplication.createApplication().run(
                "--RBAC_MIGRATION_URL=" + url, "--RBAC_MIGRATION_USER=" + username,
                "--RBAC_MIGRATION_PASSWORD=" + password)) { }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Explicit mysql-check requires " + name);
        return value;
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            if (!rows.next()) throw new IllegalStateException("Expected one verification row");
            return rows.getString(1);
        }
    }

    private static void assertSqlError(Connection connection, String sql, int code) {
        assertThatThrownBy(() -> execute(connection, sql)).isInstanceOfSatisfying(SQLException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(code));
    }
}
