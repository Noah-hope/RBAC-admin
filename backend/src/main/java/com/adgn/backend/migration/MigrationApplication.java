package com.adgn.backend.migration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;
import org.springframework.web.context.WebApplicationContext;

import java.util.Arrays;
import java.util.Map;

/** Explicit one-shot migration process; no component scan, application services, or HTTP server. */
public final class MigrationApplication {
    private MigrationApplication() { }

    public static void main(String[] args) {
        try (var context = createApplication().run(args)) {
            // Flyway initialization completed during refresh; close instead of serving requests.
        }
    }

    public static SpringApplication createApplication() {
        var application = new SpringApplication(MigrationConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles("migrate");
        application.setRegisterShutdownHook(false);
        application.addInitializers(MigrationApplication::configureMigration);
        return application;
    }

    private static void configureMigration(ConfigurableApplicationContext context) {
        if (context instanceof WebApplicationContext) {
            throw new IllegalStateException("Migration must use a non-Web application context");
        }
        var environment = context.getEnvironment();
        if (Arrays.stream(environment.getActiveProfiles()).anyMatch(profile -> !profile.equals("migrate"))) {
            throw new IllegalStateException("Migration cannot combine migrate with other profiles");
        }
        String url = required(context, "RBAC_MIGRATION_URL");
        String user = required(context, "RBAC_MIGRATION_USER");
        String password = required(context, "RBAC_MIGRATION_PASSWORD");
        if (!url.startsWith("jdbc:mysql://")) {
            throw new IllegalStateException("RBAC_MIGRATION_URL must be a MySQL JDBC URL");
        }
        // No fallback to runtime credentials or generic SPRING_FLYWAY_* overrides.
        environment.getPropertySources().addFirst(new MapPropertySource("dedicatedMigrationIdentity", Map.of(
                "spring.flyway.url", url, "spring.flyway.user", user,
                "spring.flyway.password", password, "spring.flyway.enabled", true)));
    }

    private static String required(ConfigurableApplicationContext context, String key) {
        String value = context.getEnvironment().getProperty(key);
        if (!StringUtils.hasText(value)) throw new IllegalStateException("Migration requires " + key);
        return value;
    }

    // Deliberately not @Configuration/@Component: the ordinary application must not discover this source.
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class, DataJpaRepositoriesAutoConfiguration.class})
    static class MigrationConfiguration { }
}
