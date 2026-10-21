package com.adgn.backend.demo;
import com.adgn.backend.change.FixedRequestStore;
import com.adgn.backend.core.transaction.ReadBoundary;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
@Configuration(proxyBeanMethods=false)
@Profile("demo & !migrate")
@ConditionalOnProperty(prefix="rbac.identity",name="enabled",havingValue="true")
public class DemoConfiguration {
    @Bean DemoOaSubmissionAdapter demoOaSubmissionAdapter(DataSource ds,ReadBoundary reads,FixedRequestStore fixed,JsonMapper json){return new DemoOaSubmissionAdapter(ds,reads,fixed,json);}
}
