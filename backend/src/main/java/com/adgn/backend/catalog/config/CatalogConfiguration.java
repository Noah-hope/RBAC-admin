package com.adgn.backend.catalog.config;

import com.adgn.backend.catalog.repository.SystemRepository;
import com.adgn.backend.catalog.repository.CatalogRepository;
import com.adgn.backend.catalog.service.SystemService;
import com.adgn.backend.catalog.service.CatalogService;
import com.adgn.backend.core.transaction.CommandBoundary;
import com.adgn.backend.core.audit.AuditAppender;
import com.adgn.backend.core.idempotency.IdempotencyCoordinator;
import com.adgn.backend.core.transaction.ReadBoundary;
import com.adgn.backend.identity.service.CurrentIdentityReader;
import com.adgn.backend.query.management.ManagementAccess;
import com.adgn.backend.query.preview.PreviewService;
import com.adgn.backend.query.preview.ImpactService;
import com.adgn.backend.scope.service.ManagementContextResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.time.Clock;

@Configuration(proxyBeanMethods=false)
@Profile("!migrate")
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
public class CatalogConfiguration {
    @Bean ManagementAccess managementAccess(CurrentIdentityReader current,ManagementContextResolver contexts){return new ManagementAccess(current,contexts);}
    @Bean SystemRepository systemRepository(DataSource source){return new SystemRepository(source);}
    @Bean PreviewService previewService(DataSource source,ReadBoundary reads,ManagementAccess access,JsonMapper json,ObjectProvider<Clock> clock){return new PreviewService(source,reads,access,json,clock.getIfAvailable(Clock::systemUTC));}
    @Bean ImpactService impactService(DataSource source,PreviewService previews,JsonMapper json){return new ImpactService(source,previews,json);}
    @Bean CatalogRepository catalogRepository(DataSource source){return new CatalogRepository(source);}
    @Bean CatalogService catalogService(CommandBoundary writes,ManagementAccess access,SystemRepository systems,PreviewService previews,ImpactService impacts,ReadBoundary reads,CatalogRepository catalog,ObjectProvider<IdempotencyCoordinator> idempotency,AuditAppender audit,JsonMapper json){return new CatalogService(writes,access,systems,previews,impacts,reads,catalog,idempotency,audit,json);}
    @Bean SystemService systemService(ReadBoundary reads,ManagementAccess access,SystemRepository repository,ObjectProvider<IdempotencyCoordinator> coordinator,AuditAppender audit,JsonMapper json){return new SystemService(reads,access,repository,coordinator,audit,json);}
}
