package com.adgn.backend.catalog.config;

import com.adgn.backend.catalog.repository.SystemRepository;
import com.adgn.backend.catalog.service.SystemService;
import com.adgn.backend.core.audit.AuditAppender;
import com.adgn.backend.core.idempotency.IdempotencyCoordinator;
import com.adgn.backend.core.transaction.ReadBoundary;
import com.adgn.backend.identity.service.CurrentIdentityReader;
import com.adgn.backend.query.management.ManagementAccess;
import com.adgn.backend.scope.service.ManagementContextResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;

@Configuration(proxyBeanMethods=false)
@Profile("!migrate")
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
public class CatalogConfiguration {
    @Bean ManagementAccess managementAccess(CurrentIdentityReader current,ManagementContextResolver contexts){return new ManagementAccess(current,contexts);}
    @Bean SystemRepository systemRepository(DataSource source){return new SystemRepository(source);}
    @Bean SystemService systemService(ReadBoundary reads,ManagementAccess access,SystemRepository repository,ObjectProvider<IdempotencyCoordinator> coordinator,AuditAppender audit,JsonMapper json){return new SystemService(reads,access,repository,coordinator,audit,json);}
}
