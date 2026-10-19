package com.adgn.backend.people;

import com.adgn.backend.core.transaction.TransactionBoundaryAutoConfiguration;
import com.adgn.backend.core.transaction.ReadBoundary;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import javax.sql.DataSource;

@AutoConfiguration(after=TransactionBoundaryAutoConfiguration.class)
@ConditionalOnBean({DataSource.class,ReadBoundary.class})
@Profile("!migrate")
public class PeopleAutoConfiguration {
    @Bean OrganizationReader organizationReader(DataSource source) { return new OrganizationReader(source); }
}
