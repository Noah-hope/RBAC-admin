package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.rbac.rbac1ddd.application.request.RoleCreationDetailsResult;
import java.util.List;

/**
 * 角色创建申请详情（RoleCreationRequestDetails）：申请字段平铺在顶层，另加有效权限预览，与 rbac1-structured 相同。
 *
 * @param request 申请，字段平铺输出
 * @param effectivePermissions 有效权限预览；有父角色已删除时输出 null
 */
public record RoleCreationDetailsVO(
    @JsonUnwrapped
    RoleCreationRequestVO request,
    List<EffectivePermissionVO> effectivePermissions) {

  /** 由应用层结果转换。 */
  public static RoleCreationDetailsVO from(RoleCreationDetailsResult result) {
    return new RoleCreationDetailsVO(
        RoleCreationRequestVO.from(result.request()),
        result.effectivePermissions() == null
            ? null
            : result.effectivePermissions().stream().map(EffectivePermissionVO::from).toList());
  }
}
