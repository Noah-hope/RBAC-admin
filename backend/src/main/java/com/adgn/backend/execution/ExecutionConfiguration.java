package com.adgn.backend.execution;
import com.adgn.backend.change.*;
import com.adgn.backend.core.transaction.CommandBoundary;
import com.adgn.backend.grant.*;
import com.adgn.backend.role.repository.RoleRepository;
import com.adgn.backend.role.service.RoleConfigurationApplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
@Configuration(proxyBeanMethods=false)
@Profile("!migrate")
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
public class ExecutionConfiguration {
    @Bean ExecutionService executionService(DataSource ds,CommandBoundary writes,FixedRequestStore fixed,DraftRepository drafts,GrantReader grants,GrantApplier applier,RoleRepository roles,RoleConfigurationApplier configuration,DraftPlanner planner,ClaimService claims,WorkflowStateWriter states,JsonMapper json){return new ExecutionService(ds,writes,fixed,drafts,grants,applier,roles,configuration,planner,claims,states,json);}
}
