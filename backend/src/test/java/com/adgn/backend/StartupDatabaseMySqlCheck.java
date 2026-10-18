package com.adgn.backend;

import com.adgn.backend.testsupport.IsolatedMySqlDatabase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupDatabaseMySqlCheck {
    @Test
    void refusesStartupWhenGuardRowIsMissingAndDoesNotRecreateIt() throws Exception {
        try (var database = new IsolatedMySqlDatabase()) {
            database.execute("DELETE FROM mutation_guard");
            assertThatThrownBy(() -> {
                try (var ignored = database.start(BackendApplication.class)) { }
            }).hasStackTraceContaining("RBAC_DB_NOT_INITIALIZED");
            assertThat(database.scalar("SELECT COUNT(*) FROM mutation_guard")).isEqualTo("0");
            assertThat(database.scalar("SELECT COUNT(*) FROM flyway_schema_history")).isEqualTo("3");
        }
    }

    @Test
    void refusesStartupWhenGuardTableIsMissingAndDoesNotCreateSchema() throws Exception {
        try (var database = new IsolatedMySqlDatabase()) {
            database.execute("DROP TABLE mutation_guard");
            assertThatThrownBy(() -> {
                try (var ignored = database.start(BackendApplication.class)) { }
            }).hasStackTraceContaining("RBAC_DB_NOT_INITIALIZED");
            assertThat(database.scalar("SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema=DATABASE() AND table_name='mutation_guard'")).isEqualTo("0");
        }
    }

    @Test
    void acceptsExistingNonzeroGuardWithoutResettingItOrRunningMigrations() throws Exception {
        try (var database = new IsolatedMySqlDatabase()) {
            database.execute("UPDATE mutation_guard SET revision=7 WHERE id=1");
            try (var context = database.start(BackendApplication.class)) {
                assertThat(context.isActive()).isTrue();
                assertThat(database.scalar("SELECT revision FROM mutation_guard WHERE id=1")).isEqualTo("7");
                assertThat(database.scalar("SELECT COUNT(*) FROM flyway_schema_history")).isEqualTo("3");
            }
        }
    }
}
