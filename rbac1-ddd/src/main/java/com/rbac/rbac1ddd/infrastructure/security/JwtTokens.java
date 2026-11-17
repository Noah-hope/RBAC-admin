package com.rbac.rbac1ddd.infrastructure.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.rbac.rbac1ddd.application.auth.AuthenticationFailure;
import com.rbac.rbac1ddd.application.auth.TokenIssuer;
import com.rbac.rbac1ddd.application.auth.TokenVerifier;
import com.rbac.rbac1ddd.application.auth.VerifiedToken;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * RS256 令牌的签发与校验，与 rbac1-structured 的 JwtTokens 使用相同的声明、密钥 ID 和校验规则。
 *
 * <p>两个后端使用同一对密钥，因此能互相校验对方签发的令牌。令牌只包含 iss、sub、aud、iat、exp、jti，
 * 不携带角色和权限，权限每次实时计算。
 */
class JwtTokens implements TokenIssuer, TokenVerifier {

  /** 签名密钥的 kid，与 rbac1-structured 相同。 */
  static final String KEY_ID = "rbac-signing-key";

  private final RSAKey key;
  private final String issuer;
  private final JwtEncoder encoder;
  private final NimbusJwtDecoder decoder;

  /**
   * 用一对 RSA 密钥构造签发器和只接受指定签发者的校验器。
   *
   * @param publicKey RSA 公钥
   * @param privateKey RSA 私钥
   * @param issuer 签发者，写入 iss 并在校验时要求一致
   */
  JwtTokens(RSAPublicKey publicKey, RSAPrivateKey privateKey, String issuer) {
    this.issuer = issuer;
    key =
        new RSAKey.Builder(publicKey)
            .privateKey(privateKey)
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .keyID(KEY_ID)
            .build();
    encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
  }

  /** 签发包含 iss、sub、aud、iat、exp 和随机 jti 的 RS256 令牌。 */
  @Override
  public String issue(long userId, List<String> audience, Instant expiresAt) {
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(Long.toString(userId))
            .audience(audience)
            .issuedAt(Instant.now())
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())
            .build();
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY_ID).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  /** 校验签名、签发者和有效期，取出主体和受众；不合格时抛出认证失败。 */
  @Override
  public VerifiedToken verify(String token) {
    try {
      Jwt jwt = decoder.decode(token);
      List<String> audience = jwt.getAudience();
      return new VerifiedToken(jwt.getSubject(), audience == null ? List.of() : audience);
    } catch (JwtException error) {
      throw new AuthenticationFailure();
    }
  }

  /** 只公开公钥参数（kty、e、n、use、alg、kid），不含私钥。 */
  @Override
  public List<Map<String, Object>> publicKeys() {
    return List.of(key.toPublicJWK().toJSONObject());
  }
}
