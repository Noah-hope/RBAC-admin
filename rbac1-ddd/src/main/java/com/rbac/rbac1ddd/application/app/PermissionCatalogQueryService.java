package com.rbac.rbac1ddd.application.app;

import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.domain.app.BusinessApplication;
import com.rbac.rbac1ddd.domain.app.BusinessApplicationRepository;
import com.rbac.rbac1ddd.domain.permission.Permission;
import com.rbac.rbac1ddd.domain.permission.PermissionCatalog;
import com.rbac.rbac1ddd.domain.permission.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 查询服务：角色配置权限目录，总管理员和部门管理员只读访问。
 *
 * <p>返回原始登记的菜单和按钮，不代表某个角色的有效授权；读取目录也不意味着可以配置角色。
 */
@Service
@RequiredArgsConstructor
public class PermissionCatalogQueryService {

  private final BusinessApplicationRepository applications;
  private final PermissionRepository permissions;

  /**
   * 按应用列出全部登记的菜单和按钮。
   *
   * @param operator 当前操作者，必须是管理员
   * @return 按应用 ID 升序的权限目录
   */
  @Transactional(readOnly = true)
  public PermissionCatalogResult catalog(Operator operator) {
    operator.requireAdministrator();
    PermissionCatalog catalog = permissions.loadCatalog();
    return new PermissionCatalogResult(
        applications.findAll().stream().map(application -> entry(application, catalog)).toList());
  }

  /** 组装一个应用及其登记的权限。 */
  private static PermissionCatalogResult.Application entry(
      BusinessApplication application, PermissionCatalog catalog) {
    return new PermissionCatalogResult.Application(
        application.getId(),
        application.getName(),
        application.getCode(),
        catalog.permissionsIn(application.getId()).stream()
            .map(PermissionCatalogQueryService::item)
            .toList());
  }

  /** 权限 → 权限摘要。 */
  private static PermissionCatalogResult.Item item(Permission permission) {
    return new PermissionCatalogResult.Item(
        permission.id(),
        permission.applicationId(),
        permission.name(),
        permission.code(),
        permission.type().name(),
        permission.parentId());
  }
}
