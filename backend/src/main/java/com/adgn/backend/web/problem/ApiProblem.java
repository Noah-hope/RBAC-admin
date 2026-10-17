package com.adgn.backend.web.problem;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Exact API 1.2.0 Problem projection; no Spring-specific extra properties. */
public record ApiProblem(String type, String title, int status, String code, String detail,
                         String requestId, List<FieldError> errors) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FieldError(String pointer, String code, String message, Integer rowNumber) {
        public FieldError(String pointer, String code, String message) {
            this(pointer, code, message, null);
        }
    }
}
