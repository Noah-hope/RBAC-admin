package com.adgn.backend.core.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import java.nio.file.*;

/** Server uses the same application, with explicit security and external persistent storage. */
@Configuration(proxyBeanMethods=false)
@Profile("server")
public class ServerSettingsConfiguration {
    @Bean InitializingBean serverSettingsValidation(Environment environment) {
        return ()->{
            if(!environment.getProperty("rbac.identity.enabled",Boolean.class,false)
                    || !environment.getProperty("rbac.idempotency.enabled",Boolean.class,false))
                throw new IllegalStateException("Server requires enabled identity and idempotency");
            try {
                var value=environment.getProperty("rbac.storage.import-directory","");
                var path=Path.of(value);
                if(value.isBlank() || !path.isAbsolute() || !Files.isDirectory(path) || !Files.isWritable(path))
                    throw new IllegalArgumentException();
            } catch(Exception invalid) {
                throw new IllegalStateException("Server requires a prepared absolute writable import directory");
            }
        };
    }
}
