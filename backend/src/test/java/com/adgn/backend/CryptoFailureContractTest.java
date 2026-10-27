package com.adgn.backend;

import com.adgn.backend.testsupport.JavaSubprocess;
import com.adgn.backend.core.idempotency.*;
import com.adgn.backend.core.transaction.PersistenceFailure;
import com.adgn.backend.identity.config.IdentityProperties;
import com.adgn.backend.identity.security.*;
import com.adgn.backend.query.preview.PreviewService;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.AbstractDataSource;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.time.*;
import java.sql.Connection;
import java.security.Security;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

class CryptoFailureContractTest {
    @TempDir Path directory;

    @Test void missingCryptoProvidersFailClosedInsideAnIsolatedJvm() throws Exception {
        var process=JavaSubprocess.command("-cp",System.getProperty("java.class.path"),Probe.class.getName(),directory.toString())
                .redirectErrorStream(true).start();
        try{
            assertThat(process.waitFor(25,TimeUnit.SECONDS)).isTrue();
            String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            assertThat(process.exitValue()).as(output).isZero();
            assertThat(output).contains("DIGEST_REFUSED","HMAC_REFUSED","TOKEN_REFUSED").doesNotContain("private-fixture");
            assertThat(java.security.MessageDigest.getInstance("SHA-256").digest(new byte[0])).hasSize(32);
        }finally{if(process.isAlive())process.destroyForcibly().waitFor();}
    }

    public static final class Probe {
        public static void main(String[] args) throws Exception {
            Path dir=Path.of(args[0]);var rsa=new RSAKeyGenerator(2048).keyID("fixture").generate();
            Path admin=dir.resolve("admin.json"),publicKey=dir.resolve("public.json");
            Files.writeString(admin,new JWKSet(rsa).toString(false));Files.writeString(publicKey,new JWKSet(rsa.toPublicJWK()).toString());
            var properties=new IdentityProperties();properties.setAdminKeyId("fixture");properties.setAdminJwkSet(admin.toString());
            properties.setBusinessJwkSet(publicKey.toString());properties.setOaJwkSet(publicKey.toString());
            var tokens=new IdentityTokens(properties,Clock.systemUTC());
            var source=new AbstractDataSource(){
                public Connection getConnection(){throw new AssertionError("digest must not connect");}
                public Connection getConnection(String a,String b){throw new AssertionError("must not connect");}
            };
            var previews=new PreviewService(source,null,null,JsonMapper.builder().build(),Clock.systemUTC());
            var ring=new HmacKeyRing("fixture",Map.of("fixture",new byte[32]));
            var request=new IdempotencyRequest(UUID.randomUUID(),"write","POST","/api/v1/a",UUID.randomUUID(),new byte[0]);
            // 故障仅作用于专属子 JVM，不改变测试主进程或应用的安全提供者。
            for(var provider:Security.getProviders())Security.removeProvider(provider.getName());
            try{previews.digest(Map.of("name","private-fixture"));throw new AssertionError("digest must reject");}
            catch(IllegalStateException expected){if(!(expected.getCause() instanceof java.security.NoSuchAlgorithmException))throw expected;System.out.println("DIGEST_REFUSED");}
            try{ring.digest("fixture",request);throw new AssertionError("hmac must reject");}
            catch(PersistenceFailure expected){if(expected.kind()!=PersistenceFailure.Kind.DEPENDENCY_UNAVAILABLE)throw expected;System.out.println("HMAC_REFUSED");}
            try{tokens.issue("account","session","jti","NORMAL",1,1,Instant.now().plusSeconds(60));throw new AssertionError("token must reject");}
            catch(IdentityFailure expected){if(expected.status()!=503||!expected.code().equals("DEPENDENCY_UNAVAILABLE"))throw expected;System.out.println("TOKEN_REFUSED");}
        }
    }
}
