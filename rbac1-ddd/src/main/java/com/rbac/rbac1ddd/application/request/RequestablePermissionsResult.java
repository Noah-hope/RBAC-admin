package com.rbac.rbac1ddd.application.request;

import java.util.List;

/**
 * 本人可申请的权限目录（RequestablePermissions）。
 *
 * @param applications 全部应用，按 ID 升序；没有可申请权限的应用分组为空
 */
public record RequestablePermissionsResult(List<RequestablePermissionsResult.Application> applications) {

  /**
   * 一个应用的可申请权限（RequestApplication）。
   *
   * @param id 应用 ID
   * @param name 应用名称
   * @param code 应用编码
   * @param groups 按菜单分组的可申请权限
   */
  public record Application(long id, String name, String code, List<Group> groups) {}

  /**
   * 一个菜单分组（RequestGroup）。
   *
   * @param menu 分组菜单
   * @param permissions 可申请的菜单或按钮
   */
  public record Group(PermissionSummaryResult menu, List<PermissionSummaryResult> permissions) {}
}
