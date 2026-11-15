package com.rbac.rbac1.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class SessionDtos {

  private SessionDtos() {}

  public record LoginRequest(
      @NotEmpty @Size(max = 32) String username,
      @NotEmpty String password,
      @NotEmpty List<@NotBlank @Pattern(regexp = "[a-z][a-z0-9_-]*") String> systems) {}

  public record SessionUser(
      long id, String username, String name, Long departmentId, String managementRole) {}

  public record LoginResult(String accessToken, String tokenType, long expiresIn) {}
}
