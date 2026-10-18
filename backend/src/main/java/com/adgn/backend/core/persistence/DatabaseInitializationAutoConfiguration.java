package com.adgn.backend.core.persistence;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/** Runs before the application becomes ready. Never creates or repairs persistence state. */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnBean(DataSource.class)
public class DatabaseInitializationAutoConfiguration {
    private static final String FAILURE = "RBAC_DB_NOT_INITIALIZED: mutation_guard must contain exactly "
            + "id=1 with a valid revision; run the independent migration process before starting the backend";

    @Bean
    SmartInitializingSingleton databaseInitializationCheck(DataSource dataSource) {
        return () -> {
            try {
                var rows = new JdbcTemplate(dataSource).query("SELECT id, revision FROM mutation_guard",
                        (result, index) -> new GuardRow(result.getInt(1), result.getObject(2, Long.class)));
                if (rows.size() != 1 || rows.getFirst().id() != 1 || rows.getFirst().revision() == null
                        || rows.getFirst().revision() < 0 || rows.getFirst().revision() > 9007199254740991L) {
                    throw new IllegalStateException(FAILURE);
                }
            } catch (DataAccessException failure) {
                throw new IllegalStateException(FAILURE, failure);
            }
        };
    }

    private record GuardRow(int id, Long revision) { }
}
