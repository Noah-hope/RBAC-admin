package com.rbac.rbac1ddd.application.app;

import java.util.List;

/**
 * 角色配置权限目录（PermissionCatalog）：按应用列出全部登记的菜单和按钮。
 *
 * @param applications 全部应用，按 ID 升序
 */
public record PermissionCatalogResult(List<PermissionCatalogResult.Application> applications) {

  /**
   * 一个应用及其权限（PermissionCatalogApplication）。
   *
   * @param id 应用 ID
   * @param name 应用名称
   * @param code 应用编码
   * @param permissions 应用登记的菜单和按钮，按 ID 升序
   */
  public record Application(long id, String name, String code, List<Item> permissions) {}

  /**
   * 权限摘要（PermissionSummary）。
   *
   * @param id 权限 ID
   * @param applicationId 所属应用 ID
   * @param name 权限名称
   * @param code 权限标识
   * @param type 权限类型名称：MENU 或 BUTTON
   * @param parentId 按钮所属菜单 ID，菜单为 null
   */
  public record Item(
      long id, long applicationId, String name, String code, String type, Long parentId) {}
}
