package com.rbac.rbac1.config;

import java.util.Arrays;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.stereotype.Component;

/**
 * 统一分层日志切面，是业务代码唯一的日志入口。
 *
 * <p>拦截 controller、service 和 mapper 三层 Spring Bean 的方法，记录开始、完成、耗时和异常类型。
 * 日志器使用目标类自身的名称，因此可以在 application.yml 中按包调整级别。
 * 不打印参数、返回值和异常堆栈，避免泄漏令牌、密码等敏感数据。
 */
@Aspect
@Component
public class LayerLoggingAspect {

  /** 数据访问层接口所在的包，用于从 Spring Data 代理中找回接口名。 */
  private static final String MAPPER_PACKAGE = "com.rbac.rbac1.mapper.";

  /**
   * 接口层：Controller、拦截器、异常处理器。
   */
  @Pointcut("within(com.rbac.rbac1.controller..*)")
  void controllerLayer() {}

  /**
   * 业务层：Service 及其辅助组件。
   */
  @Pointcut("within(com.rbac.rbac1.service..*)")
  void serviceLayer() {}

  /**
   * 数据访问层：Spring Data 仓库。仓库是框架生成的代理，只能按 Bean 名称匹配。
   */
  @Pointcut("bean(*Mapper)")
  void mapperLayer() {}

  /**
   * 记录接口层方法调用。
   */
  @Around("controllerLayer()")
  public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {
    return proceed(joinPoint, "接口层", joinPoint.getTarget().getClass());
  }

  /**
   * 记录业务层方法调用。
   */
  @Around("serviceLayer()")
  public Object logService(ProceedingJoinPoint joinPoint) throws Throwable {
    return proceed(joinPoint, "业务层", joinPoint.getTarget().getClass());
  }

  /**
   * 记录数据访问层方法调用。
   */
  @Around("mapperLayer()")
  public Object logMapper(ProceedingJoinPoint joinPoint) throws Throwable {
    return proceed(joinPoint, "数据访问层", mapperInterface(joinPoint.getThis()));
  }

  /**
   * 执行目标方法并输出开始、完成或异常日志。
   */
  private Object proceed(ProceedingJoinPoint joinPoint, String layer, Class<?> owner)
      throws Throwable {
    Logger logger = LoggerFactory.getLogger(owner);
    if (!logger.isDebugEnabled()) {
      return joinPoint.proceed();
    }
    String method = owner.getSimpleName() + "." + joinPoint.getSignature().getName();
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
   * 从 Spring Data 代理实现的接口中找出本项目的 Mapper 接口；找不到时退回代理类本身。
   */
  private static Class<?> mapperInterface(Object proxy) {
    return Arrays.stream(AopProxyUtils.proxiedUserInterfaces(proxy))
        .filter(type -> type.getName().startsWith(MAPPER_PACKAGE))
        .findFirst()
        .orElse(proxy.getClass());
  }

  /**
   * 计算从开始到现在经过的毫秒数。
   */
  private static long elapsedMillis(long startNanos) {
    return (System.nanoTime() - startNanos) / 1_000_000;
  }
}
