package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.auth.PermissionCheckResult;

/**
 * 菜单或按钮权限检查的响应体（PermissionCheckResult）。
 *
 * @param allowed 拥有该权限时为 true
 */
public record PermissionCheckVO(boolean allowed) {

  /** 由应用层结果逐字段转换。 */
  public static PermissionCheckVO from(PermissionCheckResult result) {
    return new PermissionCheckVO(result.allowed());
  }
}
