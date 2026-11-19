package com.rbac.rbac1.controller;

import com.rbac.rbac1.service.AuthenticationFailure;
import com.rbac.rbac1.service.ManagementFailure;
import com.rbac.rbac1.service.OperationLogService;
import com.rbac.rbac1.service.SessionUser;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.sql.SQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 统一异常处理：把认证失败、业务失败、参数错误和基础设施故障转换为 application/problem+json，code 和中文 detail 与接口契约一致。
 */
@RestControllerAdvice(basePackages = "com.rbac.rbac1.controller")
@RequiredArgsConstructor
class ApiExceptionHandler {

  private final OperationLogService logs;

  /**
   * 记录失败操作：只记录已登录用户在管理接口上的写操作，查询失败不记日志；失败记录由日志服务用独立事务保存。
   *
   * @param request 当前请求
   * @param reason 失败原因代码
   */
  private void recordFailure(HttpServletRequest request, String reason) {
    SessionUser actor = (SessionUser) request.getAttribute(SessionInterceptor.ACTOR);
    if (actor != null
        && request.getRequestURI().startsWith("/api/v1/")
        && !"GET".equals(request.getMethod())) {
      Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      logs.recordFailure(actor, request.getMethod(), route.toString(), reason);
    }
  }

  /**
   * 认证失败返回 401 或 429；登录过于频繁时带 Retry-After 头。认证失败不记操作日志。
   *
   * @param failure 认证失败
   * @return 问题详情响应
   */
  @ExceptionHandler(AuthenticationFailure.class)
  ResponseEntity<ProblemDetail> authenticationFailure(AuthenticationFailure failure) {
    ProblemDetail body =
        switch (failure.reason) {
          case UNAUTHENTICATED ->
              problem(
                  HttpStatus.UNAUTHORIZED,
                  "unauthenticated",
                  "UNAUTHENTICATED",
                  "身份校验失败",
                  "请检查登录信息或访问凭据。");
          case ACCOUNT_DISABLED ->
              problem(
                  HttpStatus.UNAUTHORIZED,
                  "account-disabled",
                  "ACCOUNT_DISABLED",
                  "账号已停用",
                  "请联系管理员确认账号状态。");
          case TOO_MANY_REQUESTS ->
              problem(
                  HttpStatus.TOO_MANY_REQUESTS,
                  "too-many-requests",
                  "TOO_MANY_REQUESTS",
                  "登录尝试过于频繁",
                  "请稍后再试。");
        };
    ResponseEntity.BodyBuilder response =
        ResponseEntity.status(body.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON);
    if (failure.retryAfterSeconds > 0) {
      response.header(HttpHeaders.RETRY_AFTER, Long.toString(failure.retryAfterSeconds));
    }
    return response.body(body);
  }

  /**
   * 业务规则拒绝：按失败原因映射状态码和 code，detail 使用业务层给出的中文提示，并记录失败操作。
   *
   * @param failure 业务失败
   * @param request 当前请求
   * @return 问题详情
   */
  @ExceptionHandler(ManagementFailure.class)
  ProblemDetail managementFailure(ManagementFailure failure, HttpServletRequest request) {
    recordFailure(request, failure.reason().code());
    return switch (failure.reason()) {
      case CONFLICT ->
          problem(HttpStatus.CONFLICT, "conflict", "CONFLICT", "当前状态不允许操作", failure.getMessage());
      case DUPLICATE ->
          problem(HttpStatus.CONFLICT, "conflict", "CONFLICT", "数据已存在", failure.getMessage());
      case FORBIDDEN ->
          problem(HttpStatus.FORBIDDEN, "forbidden", "FORBIDDEN", "操作不被允许", failure.getMessage());
      case NOT_FOUND ->
          problem(HttpStatus.NOT_FOUND, "not-found", "NOT_FOUND", "对象不存在", failure.getMessage());
      case INVALID_REQUEST ->
          problem(
              HttpStatus.BAD_REQUEST,
              "invalid-request",
              "INVALID_REQUEST",
              "请求不符合要求",
              failure.getMessage());
      case OUTSIDE_MANAGEMENT_SCOPE ->
          problem(
              HttpStatus.FORBIDDEN,
              "outside-management-scope",
              "OUTSIDE_MANAGEMENT_SCOPE",
              "超出管理范围",
              failure.getMessage());
      case APPLICATION_MISMATCH ->
          problem(
              HttpStatus.FORBIDDEN,
              "application-mismatch",
              "APPLICATION_MISMATCH",
              "应用不匹配",
              failure.getMessage());
    };
  }

  /**
   * 数据完整性冲突兜底：唯一约束冲突（MySQL 1062、H2 23505）返回 409，其余完整性错误按内部错误处理。
   *
   * @param error 数据完整性异常
   * @param request 当前请求
   * @return 问题详情
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail integrityViolation(
      DataIntegrityViolationException error,
      HttpServletRequest request) {
    recordFailure(request, "CONFLICT");
    // 已知唯一索引在触发保存的 Service 中翻译；这里只兜底其余唯一约束冲突。
    if (error.getMostSpecificCause() instanceof SQLException sql
        && (sql.getErrorCode() == 1062 || "23505".equals(sql.getSQLState()))) {
      return problem(HttpStatus.CONFLICT, "conflict", "CONFLICT", "数据已存在", "名称、应用标识、接口标识或路由已存在。");
    }
    return internalError(error);
  }

  /**
   * 请求体、路径变量或查询参数不合法时统一返回 400，不暴露具体字段的校验信息。
   *
   * @return 问题详情
   */
  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    HandlerMethodValidationException.class,
    MethodArgumentTypeMismatchException.class
  })
  ProblemDetail invalidRequest() {
    return problem(
        HttpStatus.BAD_REQUEST, "invalid-request", "INVALID_REQUEST", "请求不符合要求", "请检查请求参数及必填字段。");
  }

  /**
   * 数据库不可用或无法开启事务时返回 503。
   *
   * @return 问题详情
   */
  @ExceptionHandler({
    DataAccessResourceFailureException.class,
    CannotCreateTransactionException.class
  })
  ProblemDetail unavailable() {
    return problem(
        HttpStatus.SERVICE_UNAVAILABLE,
        "service-unavailable",
        "SERVICE_UNAVAILABLE",
        "服务暂时不可用",
        "暂时无法完成身份或权限检查。");
  }

  /**
   * 其余未预期的异常返回 500，不向客户端暴露异常信息。
   *
   * @param error 未预期的异常
   * @return 问题详情
   */
  @ExceptionHandler(RuntimeException.class)
  ProblemDetail internalError(RuntimeException error) {
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "INTERNAL_ERROR", "系统暂时不可用", "请稍后重试。");
  }

  /**
   * 构造问题详情：type 使用 urn:rbac-admin:problem: 前缀，扩展属性 code 是前端识别错误的稳定代码。
   *
   * @param status HTTP 状态
   * @param type 问题类型后缀
   * @param code 稳定错误代码
   * @param title 简短标题
   * @param detail 可直接展示的中文说明
   * @return 问题详情
   */
  private static ProblemDetail problem(
      HttpStatus status,
      String type,
      String code,
      String title,
      String detail) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
    body.setType(URI.create("urn:rbac-admin:problem:" + type));
    body.setTitle(title);
    body.setProperty("code", code);
    return body;
  }
}
