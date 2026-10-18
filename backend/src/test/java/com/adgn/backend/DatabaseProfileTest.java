package com.adgn.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the effective profile contract without opening any JDBC connection. */
class DatabaseProfileTest {
    private ApplicationContextRunner profile(String name) {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + name,
                        "SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/unused_runtime",
                        "SPRING_DATASOURCE_USERNAME=runtime_fixture",
                        "SPRING_DATASOURCE_PASSWORD=unused-runtime-fixture",
                        "RBAC_MIGRATION_URL=jdbc:mysql://localhost:3306/unused_migration",
                        "RBAC_MIGRATION_USER=migration_fixture",
                        "RBAC_MIGRATION_PASSWORD=unused-migration-fixture");
    }

    @Test
    void normalStartupDoesNotRunMigrationsOrGenerateSchema() {
        profile("dev").run(context -> {
            var env = context.getEnvironment();
            assertThat(env.getProperty("spring.flyway.enabled", Boolean.class)).isFalse();
            assertThat(env.getProperty("spring.sql.init.mode")).isEqualTo("never");
            assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
            assertThat(env.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
        });
    }

    @Test
    void runtimeReadsTheLocallyInjectedAccountAndUsesUtc() {
        profile("dev").run(context -> {
            var env = context.getEnvironment();
            assertThat(env.getProperty("spring.datasource.username")).isEqualTo("runtime_fixture");
            assertThat(env.getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:mysql://localhost:3306/unused_runtime");
            assertThat(env.getProperty("spring.jpa.properties.hibernate.jdbc.time_zone")).isEqualTo("UTC");
        });
    }

    @Test
    void migrationUsesItsOwnCredentialsAndRunsWithoutHttp() {
        profile("migrate").run(context -> {
            var env = context.getEnvironment();
            assertThat(env.getProperty("spring.main.web-application-type")).isEqualTo("none");
            assertThat(env.getProperty("spring.flyway.enabled", Boolean.class)).isTrue();
            assertThat(env.getProperty("spring.flyway.url"))
                    .isEqualTo("jdbc:mysql://localhost:3306/unused_migration");
            assertThat(env.getProperty("spring.flyway.user")).isEqualTo("migration_fixture");
            assertThat(env.getProperty("spring.flyway.password")).isEqualTo("unused-migration-fixture");
        });
    }

    @Test
    void migrationCannotSilentlyCreateSchemaOrBaselineExistingObjects() {
        profile("migrate").run(context -> {
            var env = context.getEnvironment();
            assertThat(env.getProperty("spring.flyway.create-schemas", Boolean.class)).isFalse();
            assertThat(env.getProperty("spring.flyway.baseline-on-migrate", Boolean.class)).isFalse();
            assertThat(env.getProperty("spring.flyway.clean-disabled", Boolean.class)).isTrue();
            assertThat(env.getProperty("spring.flyway.validate-on-migrate", Boolean.class)).isTrue();
            assertThat(env.getProperty("spring.flyway.out-of-order", Boolean.class)).isFalse();
        });
    }
}
