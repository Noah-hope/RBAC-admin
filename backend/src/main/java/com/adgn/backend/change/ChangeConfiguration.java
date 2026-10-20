package com.adgn.backend.change;

import com.adgn.backend.core.audit.AuditAppender;
import com.adgn.backend.core.idempotency.IdempotencyCoordinator;
import com.adgn.backend.grant.*;
import com.adgn.backend.query.management.ManagementAccess;
import com.adgn.backend.role.repository.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;

@Configuration(proxyBeanMethods=false)
@Profile("!migrate")
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
public class ChangeConfiguration {
    @Bean DraftRepository draftRepository(DataSource source,JsonMapper json){return new DraftRepository(source,json);}
    @Bean ClaimService claimService(DataSource source){return new ClaimService(source);}
    @Bean WorkflowStateWriter workflowStateWriter(DataSource source,ClaimService claims){return new WorkflowStateWriter(source,claims);}
    @Bean RoleDraftWriter roleDraftWriter(DataSource source){return new RoleDraftWriter(source);}
    @Bean DraftPlanner draftPlanner(DataSource source,RoleRepository roles,GrantReader grants,BaselineWriter baselines,JsonMapper json){return new DraftPlanner(source,roles,grants,baselines,json);}
    @Bean ChangeDraftService changeDraftService(DataSource source,ManagementAccess access,DraftRepository drafts,DraftPlanner planner,ClaimService claims,RoleDraftWriter roleWriter,RoleRepository roles,ObjectProvider<IdempotencyCoordinator> idempotency,AuditAppender audit,JsonMapper json){return new ChangeDraftService(source,access,drafts,planner,claims,roleWriter,roles,idempotency,audit,json);}
}
