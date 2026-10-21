package com.adgn.backend.imports;

import com.adgn.backend.identity.config.IdentityAutoConfiguration;
import com.adgn.backend.identity.service.CurrentIdentityReader;
import com.adgn.backend.core.audit.AuditAppender;
import com.adgn.backend.core.idempotency.*;
import com.adgn.backend.core.transaction.ReadBoundary;
import com.adgn.backend.query.preview.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.nio.file.Path;

@AutoConfiguration(after={IdentityAutoConfiguration.class,IdempotencyAutoConfiguration.class})
@ConditionalOnBean({DataSource.class,ReadBoundary.class,CurrentIdentityReader.class})
@Profile("!migrate")
public class ImportFileConfiguration {
    @Bean ImportFileParser importFileParser(){return new ImportFileParser();}
    @Bean ImportFileStore importFileStore(Environment env){
        String root=env.getProperty("rbac.storage.import-directory",env.getProperty("RBAC_IMPORT_DIRECTORY",".tmp/imports"));
        if(root.isBlank())throw new IllegalStateException("Import directory must be configured");return new ImportFileStore(Path.of(root));
    }
    @Bean ImportFileService importFileService(DataSource source,ReadBoundary reads,CurrentIdentityReader current,ObjectProvider<IdempotencyCoordinator> coordinators,
            ImportFileParser parser,ImportFileStore store,AuditAppender audit,JsonMapper json){return new ImportFileService(source,reads,current,coordinators,parser,store,audit,json);}
    @Bean ImportFailureRecorder importFailureRecorder(DataSource source,ImportPlanService reports,AuditAppender audit,JsonMapper json){return new ImportFailureRecorder(source,reports,audit,json);}
    @Bean ImportPlanner importPlanner(){return new ImportPlanner();}
    @Bean ImportCommitService importCommitService(DataSource source,ReadBoundary reads,com.adgn.backend.core.transaction.CommandBoundary writes,CurrentIdentityReader current,ObjectProvider<IdempotencyCoordinator> coordinators,ImportPlanService reports,ImportSnapshot snapshots,ImportFileService files,com.adgn.backend.people.EmploymentWriter people,AuditAppender audit,JsonMapper json,ObjectProvider<com.adgn.backend.lifecycle.EmployeeStateWriter> employeeStates,ObjectProvider<com.adgn.backend.lifecycle.EmploymentLifecycleWriter> employmentLifecycle,ImportFailureRecorder failureRecorder){return new ImportCommitService(source,reads,writes,current,coordinators,reports,snapshots,files,people,audit,json,employeeStates,employmentLifecycle,failureRecorder);}
    @Bean ImportSnapshot importSnapshot(DataSource source,PreviewService previews){return new ImportSnapshot(source,previews);}
    @Bean ImportPlanService importPlanService(DataSource source,ReadBoundary reads,CurrentIdentityReader current,ObjectProvider<IdempotencyCoordinator> coordinators,ImportFileService files,ImportPlanner planner,ImportSnapshot snapshots,ImpactService impacts,PreviewService previews,AuditAppender audit,JsonMapper json){return new ImportPlanService(source,reads,current,coordinators,files,planner,snapshots,impacts,previews,audit,json);}
}
