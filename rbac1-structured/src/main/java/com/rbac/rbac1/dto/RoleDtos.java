package com.rbac.rbac1.dto;

import static com.rbac.rbac1.dto.DepartmentDtos.MAX_ID;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.hibernate.validator.constraints.CodePointLength;
import org.hibernate.validator.constraints.UniqueElements;

public final class RoleDtos {

  private RoleDtos() {}

  public static final class RoleInput {
    @NotEmpty
    @CodePointLength(max = 24)
    public String name;

    @JsonSetter(nulls = Nulls.FAIL)
    @CodePointLength(max = 48)
    public String description = "";

    @NotNull @UniqueElements public List<@NotNull @Positive @Max(MAX_ID) Long> permissionIds;

    @NotNull @UniqueElements public List<@NotNull @Positive @Max(MAX_ID) Long> inheritedRoleIds;
  }

  public record RoleApplicationSummary(
      long applicationId, String applicationName, long permissionCount) {}

  public record EffectivePermission(long id, long applicationId, String name, String code,
      String type, Long parentId, String acquisition, List<UserDtos.RoleRef> sourceRoles) {}

  public record RoleSummary(long id, String name, String description,
      List<RoleApplicationSummary> applications, String roleType,
      List<UserDtos.RoleRef> inheritedRoles, List<UserDtos.RoleRef> inheritedByRoles,
      int directPermissionCount, int effectivePermissionCount) {}

  public record RoleDetails(long id, String name, String description,
      List<RoleApplicationSummary> applications, String roleType,
      List<UserDtos.RoleRef> inheritedRoles, List<UserDtos.RoleRef> inheritedByRoles,
      int directPermissionCount, int effectivePermissionCount, List<Long> permissionIds,
      List<EffectivePermission> effectivePermissions, long assignedUserCount) {}

  public record RolePage(List<RoleSummary> items, int page, int pageSize, long total) {}
}
