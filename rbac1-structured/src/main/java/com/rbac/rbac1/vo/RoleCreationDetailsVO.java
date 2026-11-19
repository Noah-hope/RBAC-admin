package com.rbac.rbac1.vo;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.List;

/**
 * 角色创建申请详情：申请字段平铺在顶层，另附申请配置的有效权限。
 *
 * @param request 申请本身
 * @param effectivePermissions 按申请配置计算的有效权限
 */
public record RoleCreationDetailsVO(
    @JsonUnwrapped
    RoleCreationRequestVO request,
    List<EffectivePermissionVO> effectivePermissions) {}
