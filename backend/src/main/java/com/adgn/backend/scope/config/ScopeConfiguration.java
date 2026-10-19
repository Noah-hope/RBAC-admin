package com.adgn.backend.scope.config;

import com.adgn.backend.core.transaction.ReadBoundary;
import com.adgn.backend.identity.service.CurrentIdentityReader;
import com.adgn.backend.scope.repository.*;
import com.adgn.backend.scope.service.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
@Profile("!migrate")
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
public class ScopeConfiguration {
    @Bean ScopeCoveragePolicy scopeCoveragePolicy(){return new ScopeCoveragePolicy();}
    @Bean ManagementContextResolver managementContextResolver(javax.sql.DataSource source,CurrentIdentityReader identity,ScopeRepository scopes,AppointmentRepository appointments,com.adgn.backend.people.OrganizationReader people){return new ManagementContextResolver(source,identity,scopes,appointments,people);}
    @Bean ScopeQueryService scopeQueryService(ReadBoundary reads,CurrentIdentityReader identity,ScopeRepository scopes,AppointmentRepository appointments){return new ScopeQueryService(reads,identity,scopes,appointments);}
}
