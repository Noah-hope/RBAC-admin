package com.adgn.backend.lifecycle;

import com.adgn.backend.core.audit.AuditAppender;
import com.adgn.backend.core.idempotency.IdempotencyCoordinator;
import com.adgn.backend.core.transaction.CommandBoundary;
import com.adgn.backend.grant.*;
import com.adgn.backend.change.WorkflowStateWriter;
import com.adgn.backend.query.change.RequestVisibility;
import com.adgn.backend.query.management.ManagementAccess;
import com.adgn.backend.query.preview.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;

@Configuration(proxyBeanMethods=false)
@Profile("!migrate")
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
public class LifecycleConfiguration {
    @Bean LifecycleStore lifecycleStore(DataSource source,com.adgn.backend.identity.repository.IdentityRepository identities){return new LifecycleStore(source,identities);}
    @Bean EmployeeStateWriter employeeStateWriter(LifecycleStore db,SourceCloser closer,RequestInvalidator invalidator,AuditAppender audit,JsonMapper json){return new EmployeeStateWriter(db,closer,invalidator,audit,json);}
    @Bean EmploymentLifecycleWriter employmentLifecycleWriter(LifecycleStore db,SourceCloser closer,RequestInvalidator invalidator,AuditAppender audit,JsonMapper json){return new EmploymentLifecycleWriter(db,closer,invalidator,audit,json);}
    @Bean ScopeReplacementService scopeReplacementService(LifecycleStore db,RequestInvalidator invalidator,AuditAppender audit,JsonMapper json){return new ScopeReplacementService(db,invalidator,audit,json);}
    @Bean LifecycleService lifecycleService(LifecycleStore db,ManagementAccess access,CommandBoundary commands,ObjectProvider<IdempotencyCoordinator> idempotency,PreviewService previews,ImpactService impacts,EmployeeStateWriter employees,ScopeReplacementService scopes,JsonMapper json){return new LifecycleService(db,access,commands,idempotency,previews,impacts,employees,scopes,json);}
    @Bean RequestInvalidator requestInvalidator(DataSource source,WorkflowStateWriter states,AuditAppender audit,JsonMapper json){return new RequestInvalidator(source,states,audit,json);}
    @Bean SourceClosureService sourceClosureService(DataSource source,ManagementAccess access,CommandBoundary commands,ObjectProvider<IdempotencyCoordinator> idempotency,
            GrantReader grants,SourceCloser closer,PreviewService previews,ImpactService impacts,AuditAppender audit,JsonMapper json,RequestInvalidator invalidator,RequestVisibility requests){
        return new SourceClosureService(source,access,commands,idempotency,grants,closer,previews,impacts,audit,json,invalidator,requests);
    }
}
