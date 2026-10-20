package com.adgn.backend.grant;

import com.adgn.backend.people.*;
import com.adgn.backend.core.transaction.ReadBoundary;
import com.adgn.backend.identity.service.CurrentIdentityReader;
import com.adgn.backend.scope.service.ManagementContextResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.*;
import javax.sql.DataSource;

@AutoConfiguration(after=PeopleAutoConfiguration.class)
@ConditionalOnBean({DataSource.class,ReadBoundary.class})
@Profile("!migrate")
public class GrantConfiguration {
    @Bean GrantReader grantReader(DataSource source,OrganizationReader people){return new GrantReader(source,people);}
    @Bean @ConditionalOnBean(CurrentIdentityReader.class)
    GrantQueries grantQueries(DataSource source,ReadBoundary reads,ManagementContextResolver contexts,GrantReader reader){return new GrantQueries(source,reads,contexts,reader);}
}
