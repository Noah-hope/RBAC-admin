package com.rbac.rbac1ddd.application.request;

import com.rbac.rbac1ddd.application.role.EffectivePermissionResult;
import java.util.List;

/**
 * 角色创建申请详情（RoleCreationDetails）：申请本身和按父角色当前配置计算的有效权限预览。
 *
 * @param request 申请
 * @param effectivePermissions 有效权限预览；有父角色已删除时为 null
 */
public record RoleCreationDetailsResult(
    RoleCreationRequestResult request, List<EffectivePermissionResult> effectivePermissions) {}
