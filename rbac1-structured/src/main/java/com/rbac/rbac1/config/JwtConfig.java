package com.rbac.rbac1.config;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;

/** JWT 密钥配置：从外部 PEM 文件读取 RSA 公私钥，私钥不进仓库，两个后端使用同一对密钥。 */
@Configuration(proxyBeanMethods = false)
public class JwtConfig {

  /**
   * 读取 X.509 公钥和 PKCS#8 私钥，创建 RS256 令牌组件。
   *
   * @param publicKey 公钥 PEM 资源
   * @param privateKey 私钥 PEM 资源
   * @param issuer 令牌签发方，校验时要求一致
   * @return 令牌签发与校验组件
   * @throws IOException 密钥文件无法读取时启动失败
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
