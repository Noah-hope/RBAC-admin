package com.adgn.idempotencyfixture;
import com.adgn.backend.core.idempotency.*;
import com.adgn.backend.core.transaction.CommandBoundary;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Clock;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class IdempotencyConfigTest {
    private final ApplicationContextRunner runner=new ApplicationContextRunner().withUserConfiguration(Fixture.class,IdempotencyAutoConfiguration.class);
    @Test void disabledByDefaultDoesNotRequireSecretsOrOfferUnprotectedCoordinator() {
        runner.run(c->{assertThat(c).hasNotFailed();assertThat(c).doesNotHaveBean(IdempotencyCoordinator.class);});
    }
    @Test void enabledWithoutKeysRefusesStartup() {
        runner.withPropertyValues("rbac.idempotency.enabled=true").run(c->assertThat(c).hasFailed());
    }
    @Test void validEnabledConfigurationProvidesCoordinatorAndKeyRing() {
        byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);
        runner.withPropertyValues("rbac.idempotency.enabled=true","rbac.idempotency.current-key-id=current",
                "rbac.idempotency.keys-json={\"current\":\""+Base64.getEncoder().encodeToString(bytes)+"\"}")
                .run(c->{assertThat(c).hasNotFailed();assertThat(c).hasSingleBean(IdempotencyCoordinator.class);});
    }
    @Test void malformedKeyFailsWithoutEchoingSecretValueInException() {
        runner.withPropertyValues("rbac.idempotency.enabled=true","rbac.idempotency.current-key-id=current",
                "rbac.idempotency.keys-json={\"current\":\"private-fixture-invalid\"}")
                .run(c->{assertThat(c).hasFailed();assertThat(c.getStartupFailure()).hasStackTraceContaining("Invalid idempotency key configuration").hasStackTraceContaining("IllegalArgumentException");
                    assertThat(org.springframework.core.NestedExceptionUtils.getMostSpecificCause(c.getStartupFailure()).getMessage()).doesNotContain("private-fixture-invalid");});
    }
    @Test void environmentVariableNamesActivateProtectedCoordinator() {
        byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);
        runner.withInitializer(c->c.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.SystemEnvironmentPropertySource("fixture-env",Map.of(
                "RBAC_IDEMPOTENCY_ENABLED","true","RBAC_IDEMPOTENCY_CURRENT_KEY_ID","current",
                "RBAC_IDEMPOTENCY_KEYS_JSON","{\"current\":\""+Base64.getEncoder().encodeToString(bytes)+"\"}"))))
                .run(c->{assertThat(c).hasNotFailed();assertThat(c).hasSingleBean(IdempotencyCoordinator.class);});
    }
    @Configuration(proxyBeanMethods=false) static class Fixture {
        @Bean DataSource dataSource(){return new AbstractDataSource(){
            @Override public Connection getConnection(){throw new AssertionError("configuration must not connect");}
            @Override public Connection getConnection(String u,String p){throw new AssertionError("configuration must not connect");}};}
        @Bean CommandBoundary commands(DataSource source){return new CommandBoundary(new DataSourceTransactionManager(source),source,Clock.systemUTC());}
        @Bean JsonMapper json(){return JsonMapper.builder().build();}
    }
}
