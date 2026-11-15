package com.rbac.rbac0;

import com.rbac.rbac0.service.JwtTokens;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JwtTokensTest {
    @Test
    void tokenIdentifiesUserAndAudienceWithoutEmbeddingPrivileges() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var tokens = new JwtTokens((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate(), "https://identity.test");
        var value = tokens.issue(12, List.of("course-project-management"), Instant.now().plusSeconds(7200));
        var jwt = tokens.verify(value);
        assertThat(jwt.getSubject()).isEqualTo("12");
        assertThat(jwt.getAudience()).containsExactly("course-project-management");
        assertThat(jwt.getClaims()).doesNotContainKeys("roles", "permissions");
        assertThat(tokens.publicKeys().get("keys")).isNotNull();
        var publicJwk = (java.util.Map<?, ?>) ((List<?>) tokens.publicKeys().get("keys")).getFirst();
        assertThat(publicJwk.containsKey("d")).isFalse();
        assertThat(publicJwk.get("alg").toString()).isEqualTo("RS256");
    }

    @Test
    void tamperedAndExpiredTokensAreRejected() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var tokens = new JwtTokens((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate(), "https://identity.test");
        var token = tokens.issue(12, List.of("permission-center"), Instant.now().plusSeconds(7200));
        var parts = token.split("\\.");
        assertThatThrownBy(() -> tokens.verify(parts[0] + ".e30." + parts[2])).isInstanceOf(RuntimeException.class);
        var signed = new com.nimbusds.jwt.SignedJWT(new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.RS256),
                new com.nimbusds.jwt.JWTClaimsSet.Builder().subject("12").issuer("https://identity.test")
                        .issueTime(java.util.Date.from(Instant.now().minusSeconds(300)))
                        .expirationTime(java.util.Date.from(Instant.now().minusSeconds(120))).build());
        signed.sign(new com.nimbusds.jose.crypto.RSASSASigner((RSAPrivateKey) pair.getPrivate()));
        var expired = signed.serialize();
        assertThatThrownBy(() -> tokens.verify(expired)).isInstanceOf(RuntimeException.class);
    }
}

