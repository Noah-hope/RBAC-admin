package com.rbac.rbac1.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * 审核权限申请的请求体。
 *
 * @param decision APPROVE 表示通过，REJECT 表示拒绝
 * @param roleId 通过时选择的业务角色，拒绝时不提交
 */
public record PermissionReviewDTO(
    @NotNull
    @Pattern(regexp = "APPROVE|REJECT")
    String decision,
    @Positive
    Long roleId) {

  /**
   * 通过须选择业务角色，拒绝不提交角色。
   *
   * @return 选择角色与审核结论一致时为 true
   */
  @AssertTrue(message = "通过须选择业务角色，拒绝不提交角色")
  public boolean isRoleSelectionValid() {
    return "APPROVE".equals(decision) ? roleId != null : roleId == null;
  }
}
