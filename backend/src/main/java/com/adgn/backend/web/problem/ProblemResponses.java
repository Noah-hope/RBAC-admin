package com.adgn.backend.web.problem;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@Component
public class ProblemResponses {
    private final JsonMapper json;

    public ProblemResponses(JsonMapper json) { this.json = json; }

    public ApiProblem body(HttpStatusCode status, List<ApiProblem.FieldError> errors, HttpServletRequest request) {
        String code = switch (status.value()) {
            case 401 -> "AUTHENTICATION_FAILED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 409 -> "STATE_CONFLICT";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 422 -> "VALIDATION_ERROR";
            case 429 -> "RATE_LIMITED";
            case 503 -> "DEPENDENCY_UNAVAILABLE";
            case 500 -> "DATA_INTEGRITY_ERROR";
            default -> "MALFORMED_REQUEST";
        };
        HttpStatus known = HttpStatus.resolve(status.value());
        String detail = status.is5xxServerError()
                ? "请求处理失败，请使用请求ID联系维护人员。" : "请求未通过校验，请检查输入及当前操作条件。";
        return new ApiProblem("urn:rbac-admin:problem:" + code.toLowerCase(Locale.ROOT).replace('_', '-'),
                known == null ? "Request failed" : known.getReasonPhrase(), status.value(), code, detail,
                (String) request.getAttribute("rbac.requestId"), errors.stream().limit(100).toList());
    }

    public ResponseEntity<Object> entity(HttpStatusCode status, HttpHeaders originalHeaders,
                                         List<ApiProblem.FieldError> errors, HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(originalHeaders);
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        headers.setCacheControl("no-store");
        return new ResponseEntity<>(body(status, errors, request), headers, status);
    }

    public void write(HttpServletResponse response, HttpStatusCode status,
                      List<ApiProblem.FieldError> errors, HttpServletRequest request) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        json.writeValue(response.getOutputStream(), body(status, errors, request));
    }
}
