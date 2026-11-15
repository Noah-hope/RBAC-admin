package com.rbac.rbac1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.rbac.rbac1.dto.ApiDtos.PermissionSummary;
import com.rbac.rbac1.dto.RoleDtos.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class RequestDtos {
  private RequestDtos() {}

  public record PageResult<T>(List<T> items, int page, int pageSize, long total) {}

  public record PermissionRequestInput(
      @NotNull @Positive Long permissionId, @NotBlank @Size(max = 500) String reason) {}

  public record PermissionReview(
      @NotNull @Pattern(regexp = "APPROVE|REJECT") String decision, @Positive Long roleId) {
    @AssertTrue(message = "通过须选择业务角色，拒绝不提交角色")
    public boolean isRoleSelectionValid() {
      return "APPROVE".equals(decision) ? roleId != null : roleId == null;
    }
  }

  public record ApprovalOption(
      RoleDetails role, boolean selectable, List<Long> extraPermissionIds) {}

  public record PermissionRequestView(
      long id,
      long applicantUserId,
      String applicantName,
      Long applicantDepartmentId,
      PermissionSummary permission,
      String reason,
      String status,
      Long reviewerUserId,
      Long approvedRoleId,
      Instant createdAt,
      Instant reviewedAt,
      @JsonInclude(JsonInclude.Include.NON_NULL) List<ApprovalOption> approvalRoles) {}

  public record RequestGroup(PermissionSummary menu, List<PermissionSummary> permissions) {}

  public record RequestApplication(long id, String name, String code, List<RequestGroup> groups) {}

  public record RequestablePermissions(List<RequestApplication> applications) {}

  public record RoleCreationView(
      long id,
      long applicantUserId,
      String applicantName,
      String name,
      String description,
      List<Long> permissionIds,
      List<RequestInheritedRole> inheritedRoles,
      String status,
      Long reviewerUserId,
      Long createdRoleId,
      Instant createdAt,
      Instant reviewedAt) {}

  public record RequestInheritedRole(long id, String name, boolean exists) {}

  public record RoleCreationDetails(
      @JsonUnwrapped RoleCreationView request, List<EffectivePermission> effectivePermissions) {}

  public record RoleCreationReview(
      @NotNull @Pattern(regexp = "APPROVE|REJECT") String decision, @Valid RoleInput role) {
    @AssertTrue(message = "通过须提交角色配置，拒绝不提交配置")
    public boolean isRoleSelectionValid() {
      return "APPROVE".equals(decision) ? role != null : role == null;
    }
  }
}
