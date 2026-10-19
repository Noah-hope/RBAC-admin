package com.adgn.backend.role.config;

import com.adgn.backend.role.repository.RoleRepository;
import com.adgn.backend.role.service.RoleService;
import com.adgn.backend.role.service.RoleConfigurationApplier;
import com.adgn.backend.core.audit.AuditAppender;
import com.adgn.backend.core.idempotency.IdempotencyCoordinator;
import com.adgn.backend.core.transaction.ReadBoundary;
import com.adgn.backend.query.management.ManagementAccess;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;

@Configuration(proxyBeanMethods=false)
@Profile("!migrate")
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
public class RoleConfiguration {
    @Bean RoleRepository roleRepository(DataSource source){return new RoleRepository(source);}
    @Bean RoleConfigurationApplier roleConfigurationApplier(DataSource source,RoleRepository roles,JsonMapper json){return new RoleConfigurationApplier(source,roles,json);}
    @Bean RoleService roleService(ReadBoundary reads,ManagementAccess access,RoleRepository repository,ObjectProvider<IdempotencyCoordinator> coordinator,AuditAppender audit,JsonMapper json){return new RoleService(reads,access,repository,coordinator,audit,json);}
}
