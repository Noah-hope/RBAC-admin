package com.rbac.rbac0.controller;

import com.rbac.rbac0.service.AuthenticationFailure;
import com.rbac.rbac0.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import static com.rbac.rbac0.service.AuthenticationFailure.Reason.UNAUTHENTICATED;

@Component
public class SessionInterceptor implements HandlerInterceptor {

    public static final String ACTOR = "rbac.sessionUser";

    private final SessionService sessions;

    public SessionInterceptor(SessionService sessions) {
        this.sessions = sessions;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(ACTOR, sessions.currentUser(bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION))));
        return true;
    }

    static String bearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new AuthenticationFailure(UNAUTHENTICATED);
        }
        var token = authorization.substring(7).strip();
        if (token.isEmpty()) {
            throw new AuthenticationFailure(UNAUTHENTICATED);
        }
        return token;
    }
}
