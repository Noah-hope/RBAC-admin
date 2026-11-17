package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.app.PermissionCatalogResult;
import java.util.List;

/**
 * 角色配置权限目录的响应体（PermissionCatalog），字段与 rbac1-structured 相同。
 *
 * @param applications 按应用分组的权限
 */
public record PermissionCatalogVO(List<PermissionCatalogVO.Application> applications) {

  /** 由应用层结果逐层转换。 */
  public static PermissionCatalogVO from(PermissionCatalogResult result) {
    return new PermissionCatalogVO(
        result.applications().stream()
            .map(
                app ->
                    new Application(
                        app.id(),
                        app.name(),
                        app.code(),
                        app.permissions().stream()
                            .map(
                                p ->
                                    new PermissionSummary(
                                        p.id(),
                                        p.applicationId(),
                                        p.name(),
                                        p.code(),
                                        p.type(),
                                        p.parentId()))
                            .toList()))
            .toList());
  }

  /**
   * 一个应用及其权限（PermissionCatalogApplication）。
   *
   * @param id 应用 ID
   * @param name 应用名称
   * @param code 应用编码
   * @param permissions 应用登记的菜单和按钮
   */
  public record Application(
      long id, String name, String code, List<PermissionSummary> permissions) {}

  /**
   * 权限摘要（PermissionSummary）。
   *
   * @param id 权限 ID
   * @param applicationId 所属应用 ID
   * @param name 权限名称
   * @param code 权限标识
   * @param type 权限类型：MENU 或 BUTTON
   * @param parentId 按钮所属菜单 ID，菜单为 null
   */
  public record PermissionSummary(
      long id, long applicationId, String name, String code, String type, Long parentId) {}
}
