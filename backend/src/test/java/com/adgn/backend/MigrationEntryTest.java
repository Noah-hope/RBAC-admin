package com.adgn.backend;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.internal.parser.Parser;
import org.flywaydb.core.internal.parser.ParsingContext;
import org.flywaydb.core.internal.resource.StringResource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.datasource.AbstractDriverBasedDataSource;

import javax.sql.DataSource;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationEntryTest {
    private SpringApplication application() throws Exception {
        String entry = "com.adgn.backend.migration.MigrationApplication";
        assertThatCode(() -> Class.forName(entry)).as("dedicated non-Web migration entry").doesNotThrowAnyException();
        SpringApplication app = (SpringApplication) Class.forName(entry).getMethod("createApplication").invoke(null);
        app.setLogStartupInfo(false);
        return app;
    }

    @Test
    void migrationDoesNotFallBackToTheRuntimeAccount() throws Exception {
        var app = application();
        assertThatThrownBy(() -> app.run("--spring.datasource.url=jdbc:mysql://localhost:3306/unused_runtime",
                "--spring.datasource.username=runtime_fixture", "--spring.datasource.password=unused-fixture"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("RBAC_MIGRATION_URL");
    }

    @Test
    void migrationRejectsHttpStartupBeforeOpeningAPort() throws Exception {
        var app = application();
        assertThatThrownBy(() -> app.run("--spring.main.web-application-type=servlet"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("non-Web");
    }

    @Test
    void migrationContextUsesOnlyItsDedicatedFlywayAccount() throws Exception {
        var app = application();
        assertThat(app.getWebApplicationType()).isEqualTo(WebApplicationType.NONE);
        app.addPrimarySources(Set.of(DoNotExecuteSql.class));
        assertThatCode(() -> {
        try (var context = app.run("--RBAC_MIGRATION_URL=jdbc:mysql://localhost:3306/unused_migration",
                "--RBAC_MIGRATION_USER=migration_fixture", "--RBAC_MIGRATION_PASSWORD=unused-migration-fixture",
                "--spring.datasource.username=runtime_fixture", "--rbac.identity.enabled=true")) {
            var configuration = context.getBean(Flyway.class).getConfiguration();
            assertThat(configuration.getDataSource()).isInstanceOf(AbstractDriverBasedDataSource.class);
            var migrationDataSource = (AbstractDriverBasedDataSource) configuration.getDataSource();
            assertThat(migrationDataSource.getUsername()).isEqualTo("migration_fixture");
            assertThat(migrationDataSource.getUrl()).isEqualTo("jdbc:mysql://localhost:3306/unused_migration");
            assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
            assertThat(context.getBeansOfType(BackendApplication.class)).isEmpty();
            assertThat(context.containsBean("requestIdFilter")).isFalse();
            assertThat(context.getBeansOfType(com.adgn.backend.identity.service.IdentityService.class)).isEmpty();
        }
        }).doesNotThrowAnyException();
    }

    @Test
    void connectionInitializationSeparatesCharsetAndTimeZoneStatements() throws Exception {
        var app = application();
        app.addPrimarySources(Set.of(DoNotExecuteSql.class));
        try (var context = app.run("--RBAC_MIGRATION_URL=jdbc:mysql://localhost:3306/unused_migration",
                "--RBAC_MIGRATION_USER=migration_fixture", "--RBAC_MIGRATION_PASSWORD=unused-migration-fixture")) {
            var configuration = context.getBean(Flyway.class).getConfiguration();
            var parserClass = Class.forName("org.flywaydb.database.mysql.MySQLParser");
            Parser parser = (Parser) parserClass.getConstructor(Configuration.class, ParsingContext.class)
                    .newInstance(configuration, new ParsingContext());
            List<String> statements = new ArrayList<>();
            // Parse the actual script assembled by Boot, not the original YAML list.
            try (var iterator = parser.parse(new StringResource(configuration.getInitSql()))) {
                while (iterator.hasNext()) statements.add(iterator.next().getSql().strip());
            }
            assertThat(statements).containsExactly(
                    "SET NAMES utf8mb4 COLLATE utf8mb4_0900_bin", "SET time_zone = '+00:00'");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DoNotExecuteSql {
        // This test checks assembled configuration only; it never invokes Flyway.migrate().
        @Bean FlywayMigrationStrategy noSqlExecution() { return flyway -> { }; }
    }
}
