package com.rbac.rbac1ddd.infrastructure.security;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;

/** 读取 PEM 格式的 RSA 密钥对，创建令牌签发与校验组件。配置项名称与 rbac1-structured 相同。 */
@Configuration(proxyBeanMethods = false)
class JwtConfig {

  /**
   * 创建令牌签发与校验组件。
   *
   * @param publicKey X.509 格式公钥，来自配置项 rbac.jwt.public-key
   * @param privateKey PKCS#8 格式私钥，来自配置项 rbac.jwt.private-key
   * @param issuer 签发者，来自配置项 rbac.jwt.issuer
   * @throws IOException 密钥资源无法读取时抛出，阻止应用启动
   */
  @Bean
  JwtTokens jwtTokens(
      @Value("${rbac.jwt.public-key}")
      Resource publicKey,
      @Value("${rbac.jwt.private-key}")
      Resource privateKey,
      @Value("${rbac.jwt.issuer}")
      String issuer)
      throws IOException {
    try (InputStream publicInput = publicKey.getInputStream();
        InputStream privateInput = privateKey.getInputStream()) {
      return new JwtTokens(
          RsaKeyConverters.x509().convert(publicInput),
          RsaKeyConverters.pkcs8().convert(privateInput),
          issuer);
    }
  }
}
