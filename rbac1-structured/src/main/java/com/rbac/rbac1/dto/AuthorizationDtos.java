package com.rbac.rbac1.dto;

import static com.rbac.rbac1.dto.DepartmentDtos.MAX_ID;

import com.rbac.rbac1.dto.UserDtos.RoleRef;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.hibernate.validator.constraints.UniqueElements;

public final class AuthorizationDtos {

  private AuthorizationDtos() {}

  public record ReplaceUserRoles(
      @NotNull @UniqueElements List<@NotNull @Positive @Max(MAX_ID) Long> roleIds) {}

  public record UserRoleResult(long userId, List<Long> roleIds) {}

  public record BatchRoleChange(
      @NotNull @Pattern(regexp = "GRANT|REVOKE") String action,
      @NotEmpty @UniqueElements List<@NotNull @Positive @Max(MAX_ID) Long> userIds,
      @NotEmpty @UniqueElements List<@NotNull @Positive @Max(MAX_ID) Long> roleIds) {}

  public record BatchRoleResult(String action, int affectedUsers) {}

  public record MyPermissions(
      List<RoleRef> businessRoles, List<MyApplicationPermissions> applications) {}

  public record MyPermissionRef(long id, String name, String code, List<RoleRef> sourceRoles) {}

  public record MyMenu(
      long id,
      String name,
      String code,
      List<RoleRef> sourceRoles,
      List<MyPermissionRef> buttons) {}

  public record MyApplicationPermissions(
      long applicationId, String applicationName, String applicationCode, List<MyMenu> menus) {}

  public record PermissionCheckResult(boolean allowed) {}
}
