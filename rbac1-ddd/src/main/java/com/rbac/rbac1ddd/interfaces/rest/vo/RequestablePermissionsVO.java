package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.request.RequestablePermissionsResult;
import java.util.List;

/**
 * 本人可申请的权限目录（RequestablePermissions），字段与 rbac1-structured 相同。
 *
 * @param applications 全部应用的可申请权限
 */
public record RequestablePermissionsVO(List<RequestablePermissionsVO.Application> applications) {

  /** 由应用层结果逐层转换。 */
  public static RequestablePermissionsVO from(RequestablePermissionsResult result) {
    return new RequestablePermissionsVO(
        result.applications().stream()
            .map(
                app ->
                    new Application(
                        app.id(),
                        app.name(),
                        app.code(),
                        app.groups().stream()
                            .map(
                                group ->
                                    new Group(
                                        PermissionSummaryVO.from(group.menu()),
                                        group.permissions().stream()
                                            .map(PermissionSummaryVO::from)
                                            .toList()))
                            .toList()))
            .toList());
  }

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
  public record Group(PermissionSummaryVO menu, List<PermissionSummaryVO> permissions) {}
}
