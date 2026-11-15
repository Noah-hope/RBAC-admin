package com.rbac.rbac0.config;

import com.rbac.rbac0.service.JwtTokens;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;

@Configuration(proxyBeanMethods = false)
public class JwtConfig {
    @Bean
    JwtTokens jwtTokens(@Value("${rbac.jwt.public-key}") Resource publicKey,
                        @Value("${rbac.jwt.private-key}") Resource privateKey,
                        @Value("${rbac.jwt.issuer}") String issuer) throws IOException {
        try (var publicInput = publicKey.getInputStream(); var privateInput = privateKey.getInputStream()) {
            return new JwtTokens(RsaKeyConverters.x509().convert(publicInput),
                    RsaKeyConverters.pkcs8().convert(privateInput), issuer);
        }
    }
}
