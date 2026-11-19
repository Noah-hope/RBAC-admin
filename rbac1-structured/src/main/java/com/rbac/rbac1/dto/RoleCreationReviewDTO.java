package com.rbac.rbac1.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 审核角色创建申请的请求体。
 *
 * @param decision APPROVE 表示通过，REJECT 表示拒绝
 * @param role 通过时最终采用的角色配置，拒绝时不提交
 */
public record RoleCreationReviewDTO(
    @NotNull
    @Pattern(regexp = "APPROVE|REJECT")
    String decision,
    @Valid
    RoleDTO role) {

  /**
   * 通过须提交角色配置，拒绝不提交配置。
   *
   * @return 角色配置与审核结论一致时为 true
   */
  @AssertTrue(message = "通过须提交角色配置，拒绝不提交配置")
  public boolean isRoleSelectionValid() {
    return "APPROVE".equals(decision) ? role != null : role == null;
  }
}
