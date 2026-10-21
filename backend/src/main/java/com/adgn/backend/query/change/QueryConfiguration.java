package com.adgn.backend.query.change;

import com.adgn.backend.core.transaction.ReadBoundary;
import com.adgn.backend.query.audit.AuditQueryService;
import com.adgn.backend.query.management.ManagementAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import com.adgn.backend.query.authorization.AuthorizationService;
import com.adgn.backend.identity.service.CurrentIdentityReader;
import org.springframework.beans.factory.ObjectProvider;
import java.time.Clock;

@Configuration(proxyBeanMethods=false)
@Profile("!migrate")
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
public class QueryConfiguration {
    @Bean AuthorizationService authorizationService(DataSource source,ReadBoundary reads,CurrentIdentityReader current,ObjectProvider<Clock> clocks){return new AuthorizationService(source,reads,current,clocks.getIfAvailable(Clock::systemUTC));}
    @Bean RequestVisibility requestVisibility(DataSource source){return new RequestVisibility(source);}
    @Bean ChangeQueryService changeQueryService(DataSource source,ReadBoundary reads,ManagementAccess access,RequestVisibility visibility,JsonMapper json){return new ChangeQueryService(source,reads,access,visibility,json);}
    @Bean AuditQueryService auditQueryService(DataSource source,ReadBoundary reads,ManagementAccess access,RequestVisibility visibility,JsonMapper json){return new AuditQueryService(source,reads,access,visibility,json);}
}
