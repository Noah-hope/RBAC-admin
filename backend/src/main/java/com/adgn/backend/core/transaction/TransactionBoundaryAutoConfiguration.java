package com.adgn.backend.core.transaction;

import com.adgn.backend.core.audit.AuditAppender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.time.Clock;

@AutoConfiguration(after = {HibernateJpaAutoConfiguration.class, DataSourceTransactionManagerAutoConfiguration.class})
@ConditionalOnBean({DataSource.class, PlatformTransactionManager.class})
public class TransactionBoundaryAutoConfiguration {
    @Bean @ConditionalOnMissingBean
    CommandBoundary commandBoundary(PlatformTransactionManager manager, DataSource source, ObjectProvider<Clock> clock) {
        return new CommandBoundary(manager, source, clock.getIfAvailable(Clock::systemUTC));
    }
    @Bean @ConditionalOnMissingBean
    ReadBoundary readBoundary(PlatformTransactionManager manager, DataSource source) { return new ReadBoundary(manager, source); }
    @Bean @ConditionalOnMissingBean
    AuditAppender auditAppender(DataSource source, JsonMapper json) { return new AuditAppender(source, json); }
}
