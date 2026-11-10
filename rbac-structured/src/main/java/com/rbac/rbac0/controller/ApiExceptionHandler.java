package com.rbac.rbac0.controller;

import java.net.URI;
import java.sql.SQLException;
import java.util.Map;

import com.rbac.rbac0.service.AuthenticationFailure;
import com.rbac.rbac0.service.ManagementFailure;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {
        SessionController.class, DepartmentController.class, UserController.class, ApplicationController.class
})
class ApiExceptionHandler {

    private static final Map<String, String> DUPLICATE_DETAILS = Map.of(
            "uq_user_username", "该用户名已存在。",
            "uq_application_name", "该应用名称已存在。",
            "uq_permission_application_code", "该应用内已存在相同的权限标识。"
    );

    @ExceptionHandler(AuthenticationFailure.class)
    ResponseEntity<ProblemDetail> authenticationFailure(AuthenticationFailure failure) {
        var body = switch (failure.reason) {
            case UNAUTHENTICATED -> problem(HttpStatus.UNAUTHORIZED,
                    "unauthenticated", "UNAUTHENTICATED", "身份校验失败", "请检查登录信息或访问凭据。");
            case ACCOUNT_DISABLED -> problem(HttpStatus.FORBIDDEN,
                    "account-disabled", "ACCOUNT_DISABLED", "账号已停用", "请联系管理员确认账号状态。");
            case TOO_MANY_REQUESTS -> problem(HttpStatus.TOO_MANY_REQUESTS,
                    "too-many-requests", "TOO_MANY_REQUESTS", "登录尝试过于频繁", "请稍后再试。");
        };
        var response = ResponseEntity.status(body.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (failure.retryAfterSeconds > 0) {
            response.header(HttpHeaders.RETRY_AFTER, Long.toString(failure.retryAfterSeconds));
        }
        return response.body(body);
    }

    @ExceptionHandler(ManagementFailure.class)
    ProblemDetail managementFailure(ManagementFailure failure) {
        return switch (failure.reason()) {
            case FORBIDDEN -> problem(HttpStatus.FORBIDDEN, "forbidden", "FORBIDDEN", "操作不被允许", failure.getMessage());
            case NOT_FOUND -> problem(HttpStatus.NOT_FOUND, "not-found", "NOT_FOUND", "对象不存在", failure.getMessage());
            case INVALID_REQUEST -> problem(HttpStatus.BAD_REQUEST, "invalid-request", "INVALID_REQUEST",
                    "请求不符合要求", failure.getMessage());
            case OUTSIDE_MANAGEMENT_SCOPE -> problem(HttpStatus.FORBIDDEN, "outside-management-scope",
                    "OUTSIDE_MANAGEMENT_SCOPE", "超出管理范围", failure.getMessage());
        };
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrityViolation(DataIntegrityViolationException error) {
        if (error.getMostSpecificCause() instanceof SQLException sql && sql.getErrorCode() == 1062) {
            for (var duplicate : DUPLICATE_DETAILS.entrySet()) {
                if (sql.getMessage().contains(duplicate.getKey())) {
                    return problem(HttpStatus.CONFLICT, "conflict", "CONFLICT", "数据已存在", duplicate.getValue());
                }
            }
        }
        return internalError(error);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail invalidRequest() {
        return problem(HttpStatus.BAD_REQUEST, "invalid-request", "INVALID_REQUEST",
                "请求不符合要求", "请检查请求参数及必填字段。");
    }

    @ExceptionHandler(RuntimeException.class)
    ProblemDetail internalError(RuntimeException error) {
        LoggerFactory.getLogger(ApiExceptionHandler.class).error("API request failed", error);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "INTERNAL_ERROR",
                "系统暂时不可用", "请稍后重试。");
    }

    private static ProblemDetail problem(HttpStatus status, String type, String code, String title, String detail) {
        var body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setType(URI.create("urn:rbac-admin:problem:" + type));
        body.setTitle(title);
        body.setProperty("code", code);
        return body;
    }
}
