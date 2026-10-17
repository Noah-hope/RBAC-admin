package com.adgn.backend.web.problem;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Container error dispatch uses the same safe response shape as MVC exceptions. */
@RestController
public class ProblemErrorController implements ErrorController {
    private final ProblemResponses problems;

    public ProblemErrorController(ProblemResponses problems) { this.problems = problems; }

    @RequestMapping("${server.error.path:${error.path:/error}}")
    ResponseEntity<Object> error(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = value instanceof Integer number && number >= 400 && number <= 599 ? number : 404;
        return problems.entity(HttpStatusCode.valueOf(status), HttpHeaders.EMPTY, List.of(), request);
    }
}
