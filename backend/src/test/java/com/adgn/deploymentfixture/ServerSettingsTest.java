package com.adgn.deploymentfixture;

import com.adgn.backend.BackendApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class ServerSettingsTest {
    @TempDir Path storage;
    // Discover just deployment configuration through the same package scan as the application.
    @Configuration(proxyBeanMethods=false)
    @ComponentScan(basePackageClasses=BackendApplication.class,useDefaultFilters=false,
            includeFilters=@ComponentScan.Filter(type=FilterType.REGEX,pattern=".*ServerSettingsConfiguration"))
    static class DeploymentSlice { }
    private ApplicationContextRunner server() {
        return new ApplicationContextRunner().withUserConfiguration(DeploymentSlice.class)
                .withPropertyValues("spring.profiles.active=server","rbac.identity.enabled=true",
                        "rbac.idempotency.enabled=true","rbac.storage.import-directory="+storage.toAbsolutePath());
    }
    @Test void serverRejectsDisabledIdentityInsteadOfStartingAnUnusableDeployment() {
        server().withPropertyValues("rbac.identity.enabled=false").run(c->assertThat(c).hasFailed());
    }
    @Test void serverRejectsDisabledIdempotency() {
        server().withPropertyValues("rbac.idempotency.enabled=false").run(c->assertThat(c).hasFailed());
    }
    @Test void serverRejectsMissingOrRelativePersistentDirectory() {
        for(var path:new String[]{"","relative-imports",storage.resolve("missing").toString()})
            server().withPropertyValues("rbac.storage.import-directory="+path).run(c->assertThat(c).hasFailed());
    }
    @Test void serverAcceptsPreparedPersistentDirectoryAndEnabledMechanisms() {
        server().run(c->assertThat(c).hasNotFailed());
    }
    @Test void localDevelopmentKeepsItsExistingOptionalMechanisms() {
        server().withPropertyValues("spring.profiles.active=dev","rbac.identity.enabled=false",
                "rbac.idempotency.enabled=false","rbac.storage.import-directory=")
                .run(c->assertThat(c).hasNotFailed());
    }
}
