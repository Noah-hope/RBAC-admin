package com.adgn.backend.oa;
import com.adgn.backend.change.*;
import com.adgn.backend.core.audit.AuditAppender;
import com.adgn.backend.core.idempotency.IdempotencyCoordinator;
import com.adgn.backend.core.transaction.CommandBoundary;
import com.adgn.backend.query.change.*;
import com.adgn.backend.query.management.ManagementAccess;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
@Configuration(proxyBeanMethods=false)
@Profile("!migrate")
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
public class OaConfiguration {
    @Bean FixedRequestStore fixedRequestStore(DataSource ds,JsonMapper json,AuditAppender audit,DraftRepository drafts){return new FixedRequestStore(ds,json,audit,drafts);}
    @Bean OaDeliveryService oaDeliveryService(DataSource ds,CommandBoundary writes,FixedRequestStore fixed,ObjectProvider<OaSubmissionPort> adapters,ManagementAccess access,RequestVisibility visibility,ChangeQueryService queries,ObjectProvider<IdempotencyCoordinator> idem,JsonMapper json){return new OaDeliveryService(ds,writes,fixed,adapters,access,visibility,queries,idem,json);}
    @Bean OaResultService oaResultService(DataSource ds,CommandBoundary writes,com.adgn.backend.identity.service.CurrentIdentityReader current,FixedRequestStore fixed,ClaimService claims,JsonMapper json){return new OaResultService(ds,writes,current,fixed,claims,json);}
}
