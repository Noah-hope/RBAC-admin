package com.rbac.rbac1ddd.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.rbac.rbac1ddd.application.auth.AuthenticationFailure;
import com.rbac.rbac1ddd.application.auth.VerifiedToken;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 令牌适配器单元测试：移植自 rbac1-structured 的 JwtTokensTest，不启动 Spring。 */
class JwtTokensTest {

  private static final String ISSUER = "https://identity.test";

  private final KeyPair pair;
  private final JwtTokens tokens;

  /** 生成一对 RSA 密钥并创建令牌适配器。 */
  JwtTokensTest() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    pair = generator.generateKeyPair();
    tokens =
        new JwtTokens((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate(), ISSUER);
  }

  @Test
  @DisplayName("RBAC0：令牌只标识用户和应用，不携带角色和权限；公开的公钥不含私钥")
  void 令牌只标识用户和应用() throws ParseException {
    String value =
        tokens.issue(12, List.of("course-project-management"), Instant.now().plusSeconds(7200));
    VerifiedToken verified = tokens.verify(value);
    assertThat(verified.subject()).isEqualTo("12");
    assertThat(verified.audience()).containsExactly("course-project-management");
    JWTClaimsSet claims = SignedJWT.parse(value).getJWTClaimsSet();
    assertThat(claims.getClaims()).doesNotContainKeys("roles", "permissions");
    assertThat(claims.getClaims()).containsKeys("iss", "sub", "aud", "iat", "exp", "jti");
    assertThat(tokens.publicKeys()).hasSize(1);
    Map<String, Object> publicJwk = tokens.publicKeys().getFirst();
    assertThat(publicJwk.containsKey("d")).isFalse();
    assertThat(publicJwk.get("alg").toString()).isEqualTo("RS256");
  }

  @Test
  @DisplayName("RBAC0：拒绝被篡改和已过期的令牌")
  void 拒绝被篡改和已过期的令牌() throws Exception {
    String token = tokens.issue(12, List.of("permission-center"), Instant.now().plusSeconds(7200));
    String[] parts = token.split("\\.");
    assertThatThrownBy(() -> tokens.verify(parts[0] + ".e30." + parts[2]))
        .isInstanceOf(AuthenticationFailure.class);
    SignedJWT signed =
        new SignedJWT(
            new JWSHeader(JWSAlgorithm.RS256),
            new JWTClaimsSet.Builder()
                .subject("12")
                .issuer(ISSUER)
                .issueTime(Date.from(Instant.now().minusSeconds(300)))
                .expirationTime(Date.from(Instant.now().minusSeconds(120)))
                .build());
    signed.sign(new RSASSASigner((RSAPrivateKey) pair.getPrivate()));
    String expired = signed.serialize();
    assertThatThrownBy(() -> tokens.verify(expired)).isInstanceOf(AuthenticationFailure.class);
  }

  @Test
  @DisplayName("RBAC0：拒绝其他签发者签发的令牌")
  void 拒绝其他签发者的令牌() {
    JwtTokens other =
        new JwtTokens(
            (RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate(), "https://other");
    String token = other.issue(12, List.of("permission-center"), Instant.now().plusSeconds(60));
    assertThatThrownBy(() -> tokens.verify(token)).isInstanceOf(AuthenticationFailure.class);
  }
}
