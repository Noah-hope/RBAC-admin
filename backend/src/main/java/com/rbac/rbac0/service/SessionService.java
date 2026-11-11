package com.rbac.rbac0.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

import com.rbac.rbac0.dto.SessionDtos.LoginRequest;
import com.rbac.rbac0.dto.SessionDtos.LoginResult;
import com.rbac.rbac0.dto.SessionDtos.SessionUser;
import com.rbac.rbac0.entity.LoginSession;
import com.rbac.rbac0.entity.UserAccount;
import com.rbac.rbac0.mapper.RoleMapper;
import com.rbac.rbac0.mapper.SessionMapper;
import com.rbac.rbac0.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.rbac.rbac0.service.AuthenticationFailure.Reason.ACCOUNT_DISABLED;
import static com.rbac.rbac0.service.AuthenticationFailure.Reason.UNAUTHENTICATED;

@Service
public class SessionService {

    private static final Duration SESSION_LIFETIME = Duration.ofHours(8);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserMapper users;
    private final RoleMapper roles;
    private final SessionMapper sessions;
    private final PasswordEncoder passwords;
    private final LoginAttempts loginAttempts;

    public SessionService(UserMapper users, RoleMapper roles, SessionMapper sessions,
                          PasswordEncoder passwords, LoginAttempts loginAttempts) {
        this.users = users;
        this.roles = roles;
        this.sessions = sessions;
        this.passwords = passwords;
        this.loginAttempts = loginAttempts;
    }

    @Transactional
    public LoginResult login(LoginRequest request, String address) {
        loginAttempts.requireAvailable(address);
        var user = users.findByUsername(request.username()).orElse(null);
        if (user == null || !passwords.matches(request.password(), user.passwordHash)) {
            loginAttempts.recordFailure(address);
            throw new AuthenticationFailure(UNAUTHENTICATED);
        }
        if (!"ACTIVE".equals(user.status)) {
            throw new AuthenticationFailure(ACCOUNT_DISABLED);
        }

        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        var token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var expiresAt = Instant.now().plus(SESSION_LIFETIME);
        var session = new LoginSession();
        session.tokenHash = TokenHash.sha256(token);
        session.userId = user.id;
        session.expiresAt = LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC);
        sessions.save(session);
        var identity = identity(user);
        loginAttempts.recordSuccess(address);
        return new LoginResult(token, expiresAt, identity);
    }

    @Transactional(readOnly = true)
    public SessionUser currentUser(String token) {
        return identity(authenticate(TokenHash.sha256(token)));
    }

    @Transactional
    public void logout(String token) {
        var tokenHash = TokenHash.sha256(token);
        authenticate(tokenHash);
        if (sessions.revoke(tokenHash) == 0) {
            throw new AuthenticationFailure(UNAUTHENTICATED);
        }
    }

    private UserAccount authenticate(byte[] tokenHash) {
        var session = sessions.findById(tokenHash)
                .filter(value -> value.expiresAt.isAfter(LocalDateTime.now(ZoneOffset.UTC)))
                .orElseThrow(() -> new AuthenticationFailure(UNAUTHENTICATED));
        return users.findById(session.userId)
                .filter(user -> "ACTIVE".equals(user.status))
                .orElseThrow(() -> new AuthenticationFailure(UNAUTHENTICATED));
    }

    private SessionUser identity(UserAccount user) {
        var managementRoles = roles.findManagementRoles(user.id);
        var managementRole = managementRoles.contains("TOTAL_ADMIN") ? "TOTAL_ADMIN"
                : managementRoles.contains("DEPARTMENT_ADMIN") ? "DEPARTMENT_ADMIN" : "NONE";
        return new SessionUser(user.id, user.username, user.name, user.departmentId, managementRole);
    }

}
