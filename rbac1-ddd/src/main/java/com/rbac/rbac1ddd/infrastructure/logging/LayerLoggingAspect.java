package com.rbac.rbac1ddd.infrastructure.logging;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 统一分层日志切面，是业务代码唯一的日志入口。
 *
 * <p>拦截接口层、应用层和基础设施层 Spring Bean 的方法，记录开始、完成、耗时和异常类型。
 * 日志器使用目标类自身的名称，因此可以在 application.yml 中按包调整级别。
 * 不打印参数、返回值和异常堆栈，避免泄漏令牌、密码等敏感数据。
 */
@Aspect
@Component
public class LayerLoggingAspect {

  /**
   * 接口层：Controller、拦截器、异常处理器。
   */
  @Pointcut("within(com.rbac.rbac1ddd.interfaces..*)")
  void interfacesLayer() {}

  /**
   * 应用层：应用服务和用例编排组件。
   */
  @Pointcut("within(com.rbac.rbac1ddd.application..*)")
  void applicationLayer() {}

  /**
   * 基础设施层：仓储实现、令牌校验，排除本切面自身。
   */
  @Pointcut("within(com.rbac.rbac1ddd.infrastructure..*) && !within(com.rbac.rbac1ddd.infrastructure.logging..*)")
  void infrastructureLayer() {}

  /**
   * 配置类及其注册的回调（如 Jackson 定制器）在启动时调用，不属于业务请求，不记录。
   */
  @Pointcut("@within(org.springframework.context.annotation.Configuration)"
      + " || execution(* org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer.*(..))")
  void configuration() {}

  /**
   * 记录接口层方法调用。
   */
  @Around("interfacesLayer() && !configuration()")
  public Object logInterfaces(ProceedingJoinPoint joinPoint) throws Throwable {
    return proceed(joinPoint, "接口层");
  }

  /**
   * 记录应用层方法调用。
   */
  @Around("applicationLayer() && !configuration()")
  public Object logApplication(ProceedingJoinPoint joinPoint) throws Throwable {
    return proceed(joinPoint, "应用层");
  }

  /**
   * 记录基础设施层方法调用。
   */
  @Around("infrastructureLayer() && !configuration()")
  public Object logInfrastructure(ProceedingJoinPoint joinPoint) throws Throwable {
    return proceed(joinPoint, "基础设施层");
  }

  /**
   * 执行目标方法并输出开始、完成或异常日志。
   */
  private Object proceed(ProceedingJoinPoint joinPoint, String layer) throws Throwable {
    Class<?> targetClass = joinPoint.getTarget().getClass();
    Logger logger = LoggerFactory.getLogger(targetClass);
    if (!logger.isDebugEnabled()) {
      return joinPoint.proceed();
    }
    String method = targetClass.getSimpleName() + "." + joinPoint.getSignature().getName();
    long start = System.nanoTime();
    logger.debug("[{}] {} 开始", layer, method);
    try {
      Object result = joinPoint.proceed();
      logger.debug("[{}] {} 完成，耗时 {} ms", layer, method, elapsedMillis(start));
      return result;
    } catch (Throwable error) {
      logger.debug(
          "[{}] {} 异常 {}，耗时 {} ms",
          layer,
          method,
          error.getClass().getSimpleName(),
          elapsedMillis(start));
      throw error;
    }
  }

  /**
   * 计算从开始到现在经过的毫秒数。
   */
  private static long elapsedMillis(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000;
  }
}
