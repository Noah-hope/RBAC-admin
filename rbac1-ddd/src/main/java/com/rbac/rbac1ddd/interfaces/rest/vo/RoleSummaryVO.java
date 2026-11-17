package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.role.RoleSummaryResult;
import java.util.List;

/**
 * 角色列表中的一行（RoleSummary）。
 *
 * @param id 角色 ID
 * @param name 角色名称
 * @param description 角色说明
 * @param applications 按应用汇总的有效权限数
 * @param roleType 角色类型
 * @param inheritedRoles 直接继承的父角色
 * @param inheritedByRoles 直接继承本角色的子角色
 * @param directPermissionCount 直接权限数
 * @param effectivePermissionCount 有效权限数（含继承）
 */
public record RoleSummaryVO(
    long id,
    String name,
    String description,
    List<RoleApplicationSummaryVO> applications,
    String roleType,
    List<RoleRefVO> inheritedRoles,
    List<RoleRefVO> inheritedByRoles,
    int directPermissionCount,
    int effectivePermissionCount) {

  /** 由应用层结果逐字段转换。 */
  public static RoleSummaryVO from(RoleSummaryResult result) {
    return new RoleSummaryVO(
        result.id(),
        result.name(),
        result.description(),
        RoleApplicationSummaryVO.fromAll(result.applications()),
        result.roleType(),
        RoleRefVO.fromAll(result.inheritedRoles()),
        RoleRefVO.fromAll(result.inheritedByRoles()),
        result.directPermissionCount(),
        result.effectivePermissionCount());
  }
}
