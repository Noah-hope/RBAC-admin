package com.adgn.backend.testsupport;

import com.adgn.backend.migration.MigrationApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

/** Owns only a random schema on an explicitly identified disposable server. */
public final class IsolatedMySqlDatabase implements AutoCloseable {
    private final Connection admin;
    private final String schema;
    private final String migrator;
    private final String runtime;
    private final String password = UUID.randomUUID().toString();
    private final String url;

    public IsolatedMySqlDatabase() throws Exception {
        int port = Integer.parseInt(required("RBAC_ISOLATED_MYSQL_PORT"));
        if (port < 1024 || port > 65535 || port == 3306) {
            throw new IllegalStateException("Require a disposable non-business local port");
        }
        Path data = Path.of(required("RBAC_ISOLATED_MYSQL_DATADIR")).toRealPath();
        if (!data.startsWith(Path.of(".tmp").toRealPath())) {
            throw new IllegalStateException("Disposable datadir must belong to backend/.tmp");
        }
        admin = DriverManager.getConnection("jdbc:mysql://127.0.0.1:" + port + "/", "root", "");
        try {
            if (!Path.of(scalar("SELECT @@datadir")).toRealPath().equals(data)
                    || !scalar("SELECT VERSION()").startsWith("8.4.")
                    || !scalar("SELECT @@log_bin").equals("0")) {
                throw new IllegalStateException("Disposable server identity or configuration mismatch");
            }
        } catch (Exception failure) {
            admin.close();
            throw failure;
        }
        String nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        schema = "rbacjpa" + nonce;
        migrator = "rbacjm" + nonce;
        runtime = "rbacja" + nonce;
        url = "jdbc:mysql://127.0.0.1:" + port + "/" + schema;
        try {
            execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin");
            execute("CREATE USER '" + migrator + "'@'localhost' IDENTIFIED BY '" + password + "'");
            execute("GRANT ALL PRIVILEGES ON `" + schema + "`.* TO '" + migrator + "'@'localhost'");
            try (var ignored = MigrationApplication.createApplication().run(
                    "--RBAC_MIGRATION_URL=" + url, "--RBAC_MIGRATION_USER=" + migrator,
                    "--RBAC_MIGRATION_PASSWORD=" + password)) { }
            execute("CREATE USER '" + runtime + "'@'localhost' IDENTIFIED BY '" + password + "'");
            execute("GRANT SELECT,INSERT,UPDATE,DELETE ON `" + schema + "`.* TO '" + runtime + "'@'localhost'");
            admin.setCatalog(schema);
        } catch (Exception failure) {
            close();
            throw failure;
        }
    }

    public ConfigurableApplicationContext start(Class<?>... sources) {
        var application = new SpringApplication(sources);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setRegisterShutdownHook(false);
        return application.run("--spring.profiles.active=dev", "--SPRING_DATASOURCE_URL=" + url,
                "--SPRING_DATASOURCE_USERNAME=" + runtime, "--SPRING_DATASOURCE_PASSWORD=" + password);
    }

    public ConfigurableApplicationContext startWeb(String... arguments) {
        var application = new SpringApplication(com.adgn.backend.BackendApplication.class);
        application.setRegisterShutdownHook(false);
        var args = new java.util.ArrayList<>(java.util.List.of("--spring.profiles.active=dev", "--server.port=0",
                "--SPRING_DATASOURCE_URL=" + url, "--SPRING_DATASOURCE_USERNAME=" + runtime,
                "--SPRING_DATASOURCE_PASSWORD=" + password));
        args.addAll(java.util.List.of(arguments));
        return application.run(args.toArray(String[]::new));
    }

    public void execute(String sql) throws SQLException {
        try (var statement = admin.createStatement()) { statement.execute(sql); }
    }

    public Connection runtimeConnection() throws SQLException {
        return DriverManager.getConnection(url, runtime, password);
    }

    /** Pass only this fixture's DML credentials to an owned bootstrap subprocess. */
    public void configureBootstrap(ProcessBuilder process) {
        process.environment().put("RBAC_BOOTSTRAP_URL", url);
        process.environment().put("RBAC_BOOTSTRAP_USER", runtime);
        process.environment().put("RBAC_BOOTSTRAP_PASSWORD", password);
        process.environment().put("RBAC_BOOTSTRAP_CONFIRM_DATABASE", schema);
    }

    public String scalar(String sql) throws SQLException {
        try (var statement = admin.createStatement(); var rows = statement.executeQuery(sql)) {
            if (!rows.next()) throw new IllegalStateException("Expected one row");
            return rows.getString(1);
        }
    }

    @Override
    public void close() throws SQLException {
        try {
            execute("DROP DATABASE IF EXISTS `" + schema + "`");
            execute("DROP USER IF EXISTS '" + migrator + "'@'localhost'");
            execute("DROP USER IF EXISTS '" + runtime + "'@'localhost'");
        } finally { admin.close(); }
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Explicit mysql-check requires " + key);
        return value;
    }
}
