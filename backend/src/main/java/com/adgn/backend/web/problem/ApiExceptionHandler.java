package com.adgn.backend.web.problem;

import com.adgn.backend.core.transaction.PersistenceFailure;
import com.adgn.backend.core.idempotency.IdempotencyFailure;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import tools.jackson.databind.exc.MismatchedInputException;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final ProblemResponses problems;

    public ApiExceptionHandler(ProblemResponses problems) { this.problems = problems; }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiProblem.FieldError(pointer(error.getField()), "INVALID_VALUE", "字段值不符合约束。"))
                .distinct().limit(100).toList();
        return problems.entity(HttpStatus.UNPROCESSABLE_ENTITY, headers, errors, servlet(request));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            String path = mismatch.getPath().stream().map(ref -> ref.getPropertyName() != null
                    ? escape(ref.getPropertyName()) : Integer.toString(ref.getIndex())).collect(Collectors.joining("/", "/", ""));
            return problems.entity(HttpStatus.UNPROCESSABLE_ENTITY, headers,
                    List.of(new ApiProblem.FieldError(boundedPointer(path), "INVALID_VALUE", "字段未知或类型不符合约束。")), servlet(request));
        }
        return problems.entity(HttpStatus.BAD_REQUEST, headers, List.of(), servlet(request));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problems.entity(status, headers, List.of(), servlet(request));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problems.entity(HttpStatus.UNPROCESSABLE_ENTITY, headers,
                List.of(new ApiProblem.FieldError(boundedPointer("/query/" + escape(ex.getParameterName())),
                        "REQUIRED", "缺少必填查询参数。")), servlet(request));
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(ServletRequestBindingException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String pointer = ex instanceof MissingRequestHeaderException missing
                ? boundedPointer("/headers/" + escape(missing.getHeaderName())) : "/";
        return problems.entity(HttpStatus.UNPROCESSABLE_ENTITY, headers,
                List.of(new ApiProblem.FieldError(pointer, "REQUIRED", "缺少必填请求字段。")), servlet(request));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problems.entity(ex.isForReturnValue() ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.UNPROCESSABLE_ENTITY,
                headers, List.of(), servlet(request));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception ex, HttpServletRequest request) {
        // Exception messages and stack traces can contain SQL, payloads or credentials.
        LOG.error("http_failure category={}", ex.getClass().getSimpleName());
        return problems.entity(HttpStatus.INTERNAL_SERVER_ERROR, HttpHeaders.EMPTY, List.of(), request);
    }

    @ExceptionHandler(IdempotencyFailure.class)
    ResponseEntity<Object> idempotencyFailure(IdempotencyFailure failure,HttpServletRequest request) {
        var status=switch(failure.code()) {
            case AUTHENTICATION_FAILED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.CONFLICT;
        };
        var template=problems.body(status,List.of(),request);
        String code=failure.code().name();
        var body=new ApiProblem("urn:rbac-admin:problem:"+code.toLowerCase(java.util.Locale.ROOT).replace('_','-'),
                template.title(),status.value(),code,template.detail(),template.requestId(),template.errors());
        var headers=new HttpHeaders();headers.setContentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON);headers.setCacheControl("no-store");
        if(failure.code()==IdempotencyFailure.Code.REQUEST_IN_PROGRESS)headers.set(HttpHeaders.RETRY_AFTER,Long.toString(Math.max(1,failure.retryAfter())));
        return new ResponseEntity<>(body,headers,status);
    }

    @ExceptionHandler(PersistenceFailure.class)
    ResponseEntity<Object> persistenceFailure(PersistenceFailure failure, HttpServletRequest request) {
        LOG.error("http_failure category={}", failure.kind());
        var status = failure.kind() == PersistenceFailure.Kind.DEPENDENCY_UNAVAILABLE
                ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.INTERNAL_SERVER_ERROR;
        return problems.entity(status, HttpHeaders.EMPTY, List.of(), request);
    }

    private static HttpServletRequest servlet(WebRequest request) {
        return ((ServletWebRequest) request).getRequest();
    }

    private static String pointer(String field) {
        return boundedPointer(Stream.of(field.replace("[", ".").replace("]", "").split("\\."))
                .map(ApiExceptionHandler::escape).collect(Collectors.joining("/", "/", "")));
    }

    private static String escape(String part) { return part.replace("~", "~0").replace("/", "~1"); }
    private static String boundedPointer(String pointer) { return pointer.length() <= 300 ? pointer : "/"; }
}
