package com.rbac.rbac0.dto;

import java.util.List;

import com.rbac.rbac0.dto.ApplicationDtos.ApplicationPermissions;
import com.rbac.rbac0.dto.UserDtos.RoleRef;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.CodePointLength;
import org.hibernate.validator.constraints.UniqueElements;

import static com.rbac.rbac0.dto.ApplicationDtos.PERMISSION_CODE;
import static com.rbac.rbac0.dto.DepartmentDtos.MAX_ID;

public final class AuthorizationDtos {

    private AuthorizationDtos() {}

    public record ReplaceUserRoles(@NotNull @UniqueElements List<@NotNull @Positive @Max(MAX_ID) Long> roleIds) {}

    public record UserRoleResult(long userId, List<Long> roleIds) {}

    public record BatchRoleChange(
            @NotNull @Pattern(regexp = "GRANT|REVOKE") String action,
            @NotEmpty @UniqueElements List<@NotNull @Positive @Max(MAX_ID) Long> userIds,
            @NotEmpty @UniqueElements List<@NotNull @Positive @Max(MAX_ID) Long> roleIds
    ) {}

    public record BatchRoleResult(String action, int affectedUsers) {}

    public record MyPermissions(List<RoleRef> businessRoles, List<ApplicationPermissions> applications) {}

    public record PermissionCheck(
            @NotNull @Positive @Max(MAX_ID) Long userId,
            @NotNull @CodePointLength(max = 64) @Pattern(regexp = PERMISSION_CODE) String permissionCode
    ) {}

    public record PermissionCheckResult(boolean allowed) {}
}
