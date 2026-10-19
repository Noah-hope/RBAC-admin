package com.adgn.backend.people;

import com.adgn.backend.core.transaction.TransactionBoundaryAutoConfiguration;
import com.adgn.backend.core.transaction.ReadBoundary;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import javax.sql.DataSource;
import com.adgn.backend.identity.config.IdentityAutoConfiguration;
import com.adgn.backend.identity.service.CurrentIdentityReader;

@AutoConfiguration(after={TransactionBoundaryAutoConfiguration.class,IdentityAutoConfiguration.class})
@ConditionalOnBean({DataSource.class,ReadBoundary.class})
@Profile("!migrate")
public class PeopleAutoConfiguration {
    @Bean OrganizationReader organizationReader(DataSource source) { return new OrganizationReader(source); }
    @Bean EmploymentWriter employmentWriter(DataSource source,com.adgn.backend.core.audit.AuditAppender audit,tools.jackson.databind.json.JsonMapper json){return new EmploymentWriter(source,audit,json);}
    @Bean @ConditionalOnBean(CurrentIdentityReader.class)
    PeopleQueries peopleQueries(DataSource source,ReadBoundary reads,com.adgn.backend.scope.service.ManagementContextResolver contexts){return new PeopleQueries(source,reads,contexts);}
}
