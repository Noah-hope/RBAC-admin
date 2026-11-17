package com.rbac.rbac1ddd.interfaces.rest;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Web 层配置：为受保护接口注册会话拦截器，并收紧 JSON 标量类型转换。 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

  private final SessionInterceptor sessionInterceptor;

  /**
   * 与 rbac1-structured 相同：管理接口 {@code /api/v1/**} 和除登录外的身份接口 {@code /auth/**}
   * 都先经过会话拦截器认证；签名公钥 {@code /.well-known/jwks.json} 公开访问。
   */
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(sessionInterceptor)
        .addPathPatterns("/api/v1/**", "/auth/**")
        .excludePathPatterns("/auth/login");
  }

  /** 与 rbac1-structured 一致：不把数字、布尔值自动转换成字符串，反之亦然。 */
  @Bean
  Jackson2ObjectMapperBuilderCustomizer strictJsonScalars() {
    return builder ->
        builder.postConfigurer(
            mapper -> {
              mapper
                  .coercionConfigFor(LogicalType.Textual)
                  .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
              mapper
                  .coercionConfigFor(LogicalType.Integer)
                  .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
              mapper
                  .coercionConfigFor(LogicalType.Boolean)
                  .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                  .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
            });
  }
}
