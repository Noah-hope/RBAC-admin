package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.role.EffectivePermissionResult;
import java.util.List;

/**
 * 角色的一条有效权限及其获得方式。
 *
 * @param id 权限 ID
 * @param applicationId 所属应用 ID
 * @param name 权限名称
 * @param code 权限编码
 * @param type 权限类型：MENU 或 BUTTON
 * @param parentId 上级权限 ID，可能为空
 * @param acquisition 获得方式：DIRECT、INHERITED 或 BOTH
 * @param sourceRoles 提供该权限的角色
 */
public record EffectivePermissionVO(
    long id,
    long applicationId,
    String name,
    String code,
    String type,
    Long parentId,
    String acquisition,
    List<RoleRefVO> sourceRoles) {

  /** 由应用层结果逐字段转换。 */
  public static EffectivePermissionVO from(EffectivePermissionResult result) {
    return new EffectivePermissionVO(
        result.id(),
        result.applicationId(),
        result.name(),
        result.code(),
        result.type(),
        result.parentId(),
        result.acquisition(),
        RoleRefVO.fromAll(result.sourceRoles()));
  }
}
