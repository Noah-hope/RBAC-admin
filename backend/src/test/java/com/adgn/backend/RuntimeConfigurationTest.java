package com.adgn.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.profiles.active=test")
            .withUserConfiguration(BackendApplication.class);

    @Test
    void failsStartupWhenRequiredInstanceIdIsMissing() {
        runner.withPropertyValues("rbac.runtime.instance-id=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsStartupForInvalidInstanceId() {
        runner.withPropertyValues("rbac.runtime.instance-id=invalid value")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void acceptsValidInstanceId() {
        runner.withPropertyValues("rbac.runtime.instance-id=local-01")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
