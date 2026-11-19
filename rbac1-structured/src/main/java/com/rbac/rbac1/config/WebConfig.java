package com.rbac.rbac1.config;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.rbac.rbac1.controller.SessionInterceptor;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Web 层配置：注册会话拦截器，并让 JSON 标量严格匹配类型。 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

  private final SessionInterceptor sessionInterceptor;

  /** 按部署来源配置跨域。过滤器直接处理浏览器预检，实际请求继续经过会话拦截器。 */
  @Bean
  CorsFilter rbacCorsFilter(
      @Value("${rbac.cors.allowed-origins}")
      String[] origins) {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(List.of(origins));
    cors.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE,
        "X-Application-Code"));
    cors.setExposedHeaders(List.of(HttpHeaders.RETRY_AFTER));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/auth/**", cors);
    source.registerCorsConfiguration("/api/v1/**", cors);
    source.registerCorsConfiguration("/.well-known/jwks.json", cors);
    return new CorsFilter(source);
  }

  /** 除登录外，管理接口和 /auth 接口都要求有效会话。 */
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(sessionInterceptor)
        .addPathPatterns("/api/v1/**", "/auth/**")
        .excludePathPatterns("/auth/login");
  }

  /**
   * 关闭 Jackson 的标量自动转换：数字不能当字符串、字符串不能当数字或布尔值，类型不符按请求不合法返回 400。
   *
   * @return ObjectMapper 定制器
   */
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
