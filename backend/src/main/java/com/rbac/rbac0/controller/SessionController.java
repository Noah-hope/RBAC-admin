package com.rbac.rbac0.controller;

import com.rbac.rbac0.dto.SessionDtos.LoginRequest;
import com.rbac.rbac0.dto.SessionDtos.LoginResult;
import com.rbac.rbac0.dto.SessionDtos.SessionUser;
import com.rbac.rbac0.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.rbac.rbac0.controller.SessionInterceptor.bearerToken;

@RestController
@RequestMapping("/api/v1")
public class SessionController {

    private final SessionService sessions;

    public SessionController(SessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping("/sessions")
    public ResponseEntity<LoginResult> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(sessions.login(request, http.getRemoteAddr()));
    }

    @GetMapping("/me")
    public SessionUser me(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return sessions.currentUser(bearerToken(authorization));
    }

    @DeleteMapping("/sessions/current")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        sessions.logout(bearerToken(authorization));
        return ResponseEntity.noContent().build();
    }

}
