package com.rbac.rbac0.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

public class JwtTokens {
  private final RSAKey key;
  private final String issuer;
  private final JwtEncoder encoder;
  private final NimbusJwtDecoder decoder;

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

  public String issue(long userId, List<String> applications, Instant expiresAt) {
    var claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(Long.toString(userId))
            .audience(applications)
            .issuedAt(Instant.now())
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())
            .build();
    var header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.getKeyID()).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  public Jwt verify(String token) {
    try {
      return decoder.decode(token);
    } catch (JwtException error) {
      throw new AuthenticationFailure(AuthenticationFailure.Reason.UNAUTHENTICATED);
    }
  }

  public Map<String, Object> publicKeys() {
    return new JWKSet(key.toPublicJWK()).toJSONObject();
  }
}
