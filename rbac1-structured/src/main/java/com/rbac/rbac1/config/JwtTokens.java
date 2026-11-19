package com.rbac.rbac1.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.rbac.rbac1.service.AuthenticationFailure;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

/** JWT 的签发与校验：使用 RS256 签名，两个后端共用同一对密钥。 */
public class JwtTokens {

  private final RSAKey key;
  private final String issuer;
  private final JwtEncoder encoder;
  private final NimbusJwtDecoder decoder;

  /**
   * 用同一对 RSA 密钥创建编码器和解码器；解码器除签名和有效期外还校验签发方。
   *
   * @param publicKey RSA 公钥，用于验签并通过 JWKS 公开
   * @param privateKey RSA 私钥，只用于签发
   * @param issuer 令牌签发方
   */
  public JwtTokens(RSAPublicKey publicKey, RSAPrivateKey privateKey, String issuer) {
    this.issuer = issuer;
    key =
        new RSAKey.Builder(publicKey)
            .privateKey(privateKey)
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.RS256)
            .keyID("rbac-signing-key")
            .build();
    encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
    decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
  }

  /**
   * 签发访问令牌：主体为用户 ID，受众为该用户可访问的应用编码，每个令牌带唯一 jti。
   *
   * @param userId 登录用户 ID
   * @param applications 令牌适用的应用编码
   * @param expiresAt 过期时间
   * @return 紧凑格式的 JWT
   */
  public String issue(long userId, List<String> applications, Instant expiresAt) {
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(Long.toString(userId))
            .audience(applications)
            .issuedAt(Instant.now())
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())
            .build();
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  /**
   * 校验令牌签名、有效期和签发方；任何失败都统一视为未认证，不向调用方暴露具体原因。
   *
   * @param token 访问令牌原文
   * @return 解析后的令牌
   */
  public Jwt verify(String token) {
    try {
      return decoder.decode(token);
    } catch (JwtException error) {
      throw new AuthenticationFailure(AuthenticationFailure.Reason.UNAUTHENTICATED);
    }
  }

  /**
   * 以 JWKS 格式导出公钥，供其他系统验签；结果不含私钥。
   *
   * @return JWKS JSON 对象
   */
  public Map<String, Object> publicKeys() {
    return new JWKSet(key.toPublicJWK()).toJSONObject();
  }
}
