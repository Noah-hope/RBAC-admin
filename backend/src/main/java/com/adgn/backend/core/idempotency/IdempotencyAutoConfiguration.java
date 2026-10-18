package com.adgn.backend.core.idempotency;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import com.adgn.backend.core.transaction.*;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Base64;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration(after=TransactionBoundaryAutoConfiguration.class)
@ConditionalOnProperty(prefix="rbac.idempotency",name="enabled",havingValue="true")
@ConditionalOnBean({DataSource.class,CommandBoundary.class})
public class IdempotencyAutoConfiguration {
    @Bean HmacKeyRing idempotencyKeys(Environment environment,JsonMapper json) {
        try {
            var current=environment.getProperty("rbac.idempotency.current-key-id");
            var encoded=environment.getProperty("rbac.idempotency.keys-json");
            if(encoded==null) throw new IllegalArgumentException();
            var object=json.readTree(encoded);
            if(object==null || !object.isObject())throw new IllegalArgumentException();
            var keys=new HashMap<String,byte[]>();
            for(var property:object.properties()) {
                if(!property.getValue().isString())throw new IllegalArgumentException();
                keys.put(property.getKey(),Base64.getDecoder().decode(property.getValue().stringValue()));
            }
            return new HmacKeyRing(current,keys);
        } catch(RuntimeException bad) {
            // Never attach parser/binding exceptions that include the original secret configuration.
            throw new IllegalArgumentException("Invalid idempotency key configuration");
        }
    }
    @Bean IdempotencyCoordinator idempotencyCoordinator(CommandBoundary commands,DataSource source,HmacKeyRing keys,JsonMapper json) {
        return new IdempotencyCoordinator(commands,source,keys,json);
    }
}
