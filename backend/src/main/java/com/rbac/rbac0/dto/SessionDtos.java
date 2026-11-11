package com.rbac.rbac0.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public final class SessionDtos {

    private SessionDtos() {}

    public record LoginRequest(
            @NotEmpty @Size(max = 32) String username,
            @NotEmpty String password
    ) {}

    public record SessionUser(long id, String username, String name, Long departmentId, String managementRole) {}

    public record LoginResult(String accessToken, Instant expiresAt, SessionUser user) {}
}
