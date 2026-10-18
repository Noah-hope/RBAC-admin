package com.adgn.backend.web;

import com.adgn.backend.web.problem.ApiProblem.FieldError;
import com.adgn.backend.web.problem.ProblemResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestIdFilter.class);
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final String HEADER = "X-Request-Id";
    private final ProblemResponses problems;

    public RequestIdFilter(ProblemResponses problems) { this.problems = problems; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var values = Collections.list(request.getHeaders(HEADER));
        boolean invalid = values.size() > 1 || (values.size() == 1 && !SAFE_ID.matcher(values.getFirst()).matches());
        String id = values.size() == 1 && !invalid ? values.getFirst() : UUID.randomUUID().toString();
        var previous = MDC.getCopyOfContextMap();
        request.setAttribute("rbac.requestId", id);
        response.setHeader(HEADER, id);
        response.setHeader("Cache-Control", "no-store");
        MDC.put("requestId", id);
        long started = System.nanoTime();
        try {
            if (invalid) {
                problems.write(response, HttpStatus.UNPROCESSABLE_ENTITY,
                        List.of(new FieldError("/headers/X-Request-Id", "INVALID_VALUE", "请求ID必须是1～64位安全字符且只能提供一次。")), request);
                return;
            }
            chain.doFilter(request, response);
        } finally {
            // Deliberately omit URL/query/body, credentials and exception text.
            LOG.info("http_request method={} status={} durationMs={}", request.getMethod(), response.getStatus(),
                    (System.nanoTime() - started) / 1_000_000);
            if (previous == null) MDC.clear(); else MDC.setContextMap(previous);
        }
    }
}
