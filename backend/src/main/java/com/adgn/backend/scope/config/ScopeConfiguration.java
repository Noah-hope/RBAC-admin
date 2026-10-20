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
    @Bean ScopeCreationFacts scopeCreationFacts(javax.sql.DataSource source,com.adgn.backend.people.OrganizationReader people){return new ScopeCreationFacts(source,people);}
    @Bean ScopeCreationRepository scopeCreationRepository(javax.sql.DataSource source,ScopeCreationJpaRepository scopes,AppointmentCreationJpaRepository appointments){return new ScopeCreationRepository(source,scopes,appointments);}
    @Bean ScopeCreationService scopeCreationService(com.adgn.backend.core.transaction.CommandBoundary writes,com.adgn.backend.query.management.ManagementAccess access,com.adgn.backend.query.preview.PreviewService previews,ScopeCreationFacts facts,ScopeCreationRepository repository,org.springframework.beans.factory.ObjectProvider<com.adgn.backend.core.idempotency.IdempotencyCoordinator> coordinator,com.adgn.backend.core.audit.AuditAppender audit,tools.jackson.databind.json.JsonMapper json){return new ScopeCreationService(writes,access,previews,facts,repository,coordinator,audit,json);}
    @Bean ScopeCoveragePolicy scopeCoveragePolicy(){return new ScopeCoveragePolicy();}
    @Bean ManagementContextResolver managementContextResolver(javax.sql.DataSource source,CurrentIdentityReader identity,ScopeRepository scopes,AppointmentRepository appointments,com.adgn.backend.people.OrganizationReader people){return new ManagementContextResolver(source,identity,scopes,appointments,people);}
    @Bean ScopeQueryService scopeQueryService(ReadBoundary reads,CurrentIdentityReader identity,ScopeRepository scopes,AppointmentRepository appointments){return new ScopeQueryService(reads,identity,scopes,appointments);}
}
