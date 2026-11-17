package com.rbac.rbac1ddd.interfaces.rest;

import com.rbac.rbac1ddd.application.auth.AuthenticationFailure;
import com.rbac.rbac1ddd.application.shared.OperationRecorder;
import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.domain.shared.DomainException;
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
 * 统一错误响应，格式与 rbac1-structured 相同（ProblemDetail + code 字段），并记录失败的写操作。
 *
 * <p>业务上的数据重复（如用户名、角色名称）已由仓储适配器就地翻译为领域异常；这里只为没有被翻译的
 * 唯一约束冲突提供统一兜底，不按约束名区分。未预料的错误由 LayerLoggingAspect 记录异常类型。
 */
@RestControllerAdvice
@RequiredArgsConstructor
class RestExceptionHandler {

  private final OperationRecorder recorder;

  /**
   * 认证失败：令牌或账号密码无效、账号停用返回 401，登录过于频繁返回 429 并带 Retry-After 头。
   *
   * <p>此时没有操作者，不记录失败日志。
   */
  @ExceptionHandler(AuthenticationFailure.class)
  ResponseEntity<ProblemDetail> authenticationFailure(AuthenticationFailure failure) {
    ProblemDetail body =
        switch (failure.getReason()) {
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
    if (failure.getRetryAfterSeconds() > 0) {
      response.header(HttpHeaders.RETRY_AFTER, Long.toString(failure.getRetryAfterSeconds()));
    }
    return response.body(body);
  }

  /**
   * 把领域规则拒绝的原因映射为 HTTP 状态和稳定错误码，中文提示沿用领域异常消息。
   *
   * <p>数据重复在结构化版本中来自数据库约束，失败原因记为 CONFLICT，这里保持一致。
   */
  @ExceptionHandler(DomainException.class)
  ProblemDetail domainFailure(DomainException failure, HttpServletRequest request) {
    recordFailure(
        request,
        failure.reason() == DomainException.Reason.DUPLICATE
            ? DomainException.Reason.CONFLICT.name()
            : failure.reason().name());
    String detail = failure.getMessage();
    return switch (failure.reason()) {
      case CONFLICT -> problem(HttpStatus.CONFLICT, "conflict", "CONFLICT", "当前状态不允许操作", detail);
      case DUPLICATE -> problem(HttpStatus.CONFLICT, "conflict", "CONFLICT", "数据已存在", detail);
      case FORBIDDEN -> problem(HttpStatus.FORBIDDEN, "forbidden", "FORBIDDEN", "操作不被允许", detail);
      case NOT_FOUND -> problem(HttpStatus.NOT_FOUND, "not-found", "NOT_FOUND", "对象不存在", detail);
      case INVALID_REQUEST ->
          problem(HttpStatus.BAD_REQUEST, "invalid-request", "INVALID_REQUEST", "请求不符合要求", detail);
      case OUTSIDE_MANAGEMENT_SCOPE ->
          problem(
              HttpStatus.FORBIDDEN,
              "outside-management-scope",
              "OUTSIDE_MANAGEMENT_SCOPE",
              "超出管理范围",
              detail);
      case APPLICATION_MISMATCH ->
          problem(HttpStatus.FORBIDDEN, "application-mismatch", "APPLICATION_MISMATCH", "应用不匹配", detail);
    };
  }

  /**
   * 请求体、路径参数或查询参数格式不合法时统一返回 400，不暴露具体字段错误。
   *
   * <p>与 rbac1-structured 相同，格式错误不写失败操作记录。
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
   * 没有被仓储翻译的数据库完整性错误：唯一约束冲突统一返回 409，其他完整性错误按未预料的错误返回 500。
   *
   * <p>与 rbac1-structured 相同，两种情况都按 CONFLICT 记录失败的写操作。
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail integrityViolation(
      DataIntegrityViolationException error, HttpServletRequest request) {
    recordFailure(request, DomainException.Reason.CONFLICT.name());
    if (error.getMostSpecificCause() instanceof SQLException sql
        && (sql.getErrorCode() == 1062 || "23505".equals(sql.getSQLState()))) {
      return problem(
          HttpStatus.CONFLICT, "conflict", "CONFLICT", "数据已存在", "名称、应用标识、接口标识或路由已存在。");
    }
    return internalError();
  }

  /** 数据库连接失败或无法开启事务时返回 503，不记录失败操作（记录本身也写不进数据库）。 */
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

  /** 其他未预料的运行时错误返回 500，不暴露内部信息。 */
  @ExceptionHandler(RuntimeException.class)
  ProblemDetail internalError() {
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "INTERNAL_ERROR", "系统暂时不可用", "请稍后重试。");
  }

  /** 已登录用户调用管理接口的写操作失败时单独记录；GET 请求和身份接口不记录。 */
  private void recordFailure(HttpServletRequest request, String reason) {
    if (request.getAttribute(SessionInterceptor.OPERATOR) instanceof Operator operator
        && SessionInterceptor.isManagementApi(request)
        && !"GET".equals(request.getMethod())) {
      Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
      recorder.recordFailure(operator, request.getMethod(), String.valueOf(route), reason);
    }
  }

  /** 构造 problem+json 响应体，type 使用 {@code urn:rbac-admin:problem:} 前缀，并附加 code 字段。 */
  private static ProblemDetail problem(
      HttpStatus status, String type, String code, String title, String detail) {
    ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
    body.setType(URI.create("urn:rbac-admin:problem:" + type));
    body.setTitle(title);
    body.setProperty("code", code);
    return body;
  }
}
