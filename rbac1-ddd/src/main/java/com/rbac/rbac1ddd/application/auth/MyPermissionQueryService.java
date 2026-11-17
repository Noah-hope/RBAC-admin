package com.rbac.rbac1ddd.application.auth;

import com.rbac.rbac1ddd.application.role.RoleRefResult;
import com.rbac.rbac1ddd.domain.app.BusinessApplication;
import com.rbac.rbac1ddd.domain.app.BusinessApplicationRepository;
import com.rbac.rbac1ddd.domain.permission.Permission;
import com.rbac.rbac1ddd.domain.permission.PermissionCatalog;
import com.rbac.rbac1ddd.domain.permission.PermissionRepository;
import com.rbac.rbac1ddd.domain.role.RoleHierarchy;
import com.rbac.rbac1ddd.domain.role.RoleRepository;
import com.rbac.rbac1ddd.domain.user.User;
import com.rbac.rbac1ddd.domain.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 查询服务：「我的权限」，所有已登录用户都可以查看自己的权限。
 *
 * <p>只做组装：来源角色由 {@link User#permissionSources} 按继承图计算，
 * 菜单与按钮的归属由 {@link PermissionCatalog} 判断，这里按应用汇总成视图。
 */
@Service
@RequiredArgsConstructor
public class MyPermissionQueryService {

  private final UserRepository users;
  private final RoleRepository roles;
  private final PermissionRepository permissions;
  private final BusinessApplicationRepository applications;

  /**
   * 按全部已登记应用汇总当前用户的已获菜单、按钮及实际配置来源。
   *
   * @param operator 当前操作者
   * @return 显式分配的业务角色和各应用的已获权限
   */
  @Transactional(readOnly = true)
  public MyPermissionsResult mine(Operator operator) {
    User user = users.findById(operator.id()).orElseThrow(AuthenticationFailure::new);
    RoleHierarchy hierarchy = roles.loadHierarchy();
    PermissionCatalog catalog = permissions.loadCatalog();
    Map<Long, Set<Long>> sources = user.permissionSources(hierarchy);
    List<MyPermissionsResult.Application> trees =
        applications.findAll().stream()
            .map(application -> tree(application, sources, hierarchy, catalog))
            .toList();
    return new MyPermissionsResult(
        RoleRefResult.listOf(hierarchy, user.businessRoleIds(hierarchy)), trees);
  }

  /** 组装一个应用中的已获菜单，每个菜单带上其下已获得的按钮。 */
  private static MyPermissionsResult.Application tree(
      BusinessApplication application,
      Map<Long, Set<Long>> sources,
      RoleHierarchy hierarchy,
      PermissionCatalog catalog) {
    List<MyPermissionsResult.Menu> menus =
        catalog.menusIn(application.getId(), sources.keySet()).stream()
            .map(
                menu ->
                    new MyPermissionsResult.Menu(
                        menu.id(),
                        menu.name(),
                        menu.code(),
                        RoleRefResult.listOf(hierarchy, sources.get(menu.id())),
                        catalog.buttonsOf(menu.id(), sources.keySet()).stream()
                            .map(button -> button(button, sources, hierarchy))
                            .toList()))
            .toList();
    return new MyPermissionsResult.Application(
        application.getId(), application.getName(), application.getCode(), menus);
  }

  /** 组装已获按钮及其来源角色。 */
  private static MyPermissionsResult.Button button(
      Permission button, Map<Long, Set<Long>> sources, RoleHierarchy hierarchy) {
    return new MyPermissionsResult.Button(
        button.id(),
        button.name(),
        button.code(),
        RoleRefResult.listOf(hierarchy, sources.get(button.id())));
  }
}
