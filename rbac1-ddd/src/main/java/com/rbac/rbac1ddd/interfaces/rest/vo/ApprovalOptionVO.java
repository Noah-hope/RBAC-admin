package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.request.ApprovalOptionResult;
import java.util.List;

/**
 * 审批候选角色（ApprovalOption），字段与 rbac1-structured 相同。
 *
 * @param role 候选角色详情
 * @param selectable 可以选择时为 true
 * @param extraPermissionIds 附带的无关权限 ID
 */
public record ApprovalOptionVO(
    RoleDetailsVO role, boolean selectable, List<Long> extraPermissionIds) {

  /** 由应用层结果逐字段转换。 */
  public static ApprovalOptionVO from(ApprovalOptionResult result) {
    return new ApprovalOptionVO(
        RoleDetailsVO.from(result.role()), result.selectable(), result.extraPermissionIds());
  }
}
