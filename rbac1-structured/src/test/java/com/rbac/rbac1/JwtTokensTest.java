package com.rbac.rbac1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.rbac.rbac1.config.JwtTokens;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** JWT 签发与校验的单元测试：每个用例生成一对新的 RSA 密钥，不启动 Spring。 */
class JwtTokensTest {

  @Test
  @DisplayName("RBAC0：令牌只标识用户和应用，不携带角色和权限；公开的公钥不含私钥")
  void tokenIdentifiesUserAndAudienceWithoutEmbeddingPrivileges() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair pair = generator.generateKeyPair();
    JwtTokens tokens =
        new JwtTokens(
            (RSAPublicKey) pair.getPublic(),
            (RSAPrivateKey) pair.getPrivate(),
            "https://identity.test");
    String value =
        tokens.issue(12, List.of("course-project-management"), Instant.now().plusSeconds(7200));
    Jwt jwt = tokens.verify(value);
    assertThat(jwt.getSubject()).isEqualTo("12");
    assertThat(jwt.getAudience()).containsExactly("course-project-management");
    assertThat(jwt.getClaims()).doesNotContainKeys("roles", "permissions");
    assertThat(tokens.publicKeys().get("keys")).isNotNull();
    Map<?, ?> publicJwk = (Map<?, ?>) ((List<?>) tokens.publicKeys().get("keys")).getFirst();
    assertThat(publicJwk.containsKey("d")).isFalse();
    assertThat(publicJwk.get("alg").toString()).isEqualTo("RS256");
  }

  @Test
  @DisplayName("RBAC0：拒绝被篡改和已过期的令牌")
  void tamperedAndExpiredTokensAreRejected() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair pair = generator.generateKeyPair();
    JwtTokens tokens =
        new JwtTokens(
            (RSAPublicKey) pair.getPublic(),
            (RSAPrivateKey) pair.getPrivate(),
            "https://identity.test");
    String token = tokens.issue(12, List.of("permission-center"), Instant.now().plusSeconds(7200));
    String[] parts = token.split("\\.");
    assertThatThrownBy(() -> tokens.verify(parts[0] + ".e30." + parts[2]))
        .isInstanceOf(RuntimeException.class);
    SignedJWT signed =
        new SignedJWT(
            new JWSHeader(JWSAlgorithm.RS256),
            new JWTClaimsSet.Builder()
                .subject("12")
                .issuer("https://identity.test")
                .issueTime(Date.from(Instant.now().minusSeconds(300)))
                .expirationTime(Date.from(Instant.now().minusSeconds(120)))
                .build());
    signed.sign(new RSASSASigner((RSAPrivateKey) pair.getPrivate()));
    String expired = signed.serialize();
    assertThatThrownBy(() -> tokens.verify(expired)).isInstanceOf(RuntimeException.class);
  }
}
