package com.adgn.backend.execution;
import com.adgn.backend.oa.OaDeliveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.*;
/** 启动和周期扫描持久状态；进程内唤醒丢失不影响恢复。 */
@Configuration(proxyBeanMethods=false)
@Profile("!migrate")
@EnableScheduling
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
@ConditionalOnProperty(prefix="rbac.workflow",name="scheduling-enabled",havingValue="true",matchIfMissing=true)
public class WorkflowRecovery {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(WorkflowRecovery.class);
    private final OaDeliveryService delivery;private final ExecutionService execution;
    public WorkflowRecovery(OaDeliveryService delivery,ExecutionService execution){this.delivery=delivery;this.execution=execution;}
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelay=30000,initialDelay=30000)
    public void recover(){try{delivery.scan();}catch(Exception e){LOG.warn("workflow_delivery_scan_unavailable");}try{execution.scan();}catch(Exception e){LOG.warn("workflow_execution_scan_unavailable");}}
}
