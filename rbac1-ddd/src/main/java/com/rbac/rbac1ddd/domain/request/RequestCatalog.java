package com.rbac.rbac1ddd.domain.request;

import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.CONFLICT;
import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.FORBIDDEN;
import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.NOT_FOUND;

import com.rbac.rbac1ddd.domain.app.BusinessApplication;
import com.rbac.rbac1ddd.domain.permission.Permission;
import com.rbac.rbac1ddd.domain.permission.PermissionCatalog;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.user.ManagementRole;
import com.rbac.rbac1ddd.domain.permission.PermissionType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 领域服务：某个用户的权限申请目录。
 *
 * <p>判断一项权限能否申请需要同时知道权限目录（菜单与按钮的上下级）、权限所属应用、申请人的全部有效权限
 * （直接和继承）以及申请人的管理身份，单个聚合都不具备，因此单独建模（GRASP 纯虚构）。
 *
 * <p>规则：未获得菜单时只能申请菜单本身；获得菜单（直接或继承）后才能申请其下未获得的按钮；
 * 已拥有的权限不再列出。权限中心只开放用户、授权、审计三类职责，并且只对管理员开放。
 */
public final class RequestCatalog {
  /** 权限中心中可以通过业务角色申请的权限编码前缀（冒号之前的资源名）。 */
  public static final Set<String> REQUESTABLE_CENTER_RESOURCES = Set.of("user", "assignment", "audit");

  private final PermissionCatalog permissions;
  private final Map<Long, BusinessApplication> applications;
  private final Set<Long> ownedPermissionIds;
  private final ManagementRole applicantRole;

  /**
   * 构造申请目录。
   *
   * @param permissions 全部菜单和按钮
   * @param applications 全部已登记应用
   * @param ownedPermissionIds 申请人当前的有效权限 ID（直接和继承）
   * @param applicantRole 申请人的管理身份
   */
  public RequestCatalog(
      PermissionCatalog permissions,
      Collection<BusinessApplication> applications,
      Set<Long> ownedPermissionIds,
      ManagementRole applicantRole) {
    this.permissions = permissions;
    this.applications =
        applications.stream()
            .collect(Collectors.toUnmodifiableMap(BusinessApplication::getId, Function.identity()));
    this.ownedPermissionIds = Set.copyOf(ownedPermissionIds);
    this.applicantRole = applicantRole;
  }

  /**
   * 一个菜单分组：菜单本身未获得时只含菜单，已获得时含其下可申请的按钮。
   *
   * @param menu 分组的菜单，作为按钮的上下文标题
   * @param permissions 可申请的权限，按 ID 升序
   */
  public record MenuGroup(Permission menu, List<Permission> permissions) {}

  /**
   * 列出应用中可申请的权限，按菜单分组；没有可申请权限的菜单不列出。
   *
   * @param applicationId 应用 ID
   * @return 按菜单 ID 升序排列的分组
   */
  public List<MenuGroup> groupsIn(long applicationId) {
    List<Permission> all = permissions.permissionsIn(applicationId);
    List<MenuGroup> groups = new ArrayList<>();
    for (Permission menu : all) {
      if (menu.type() != PermissionType.MENU || !eligible(menu)) continue;
      List<Permission> available =
          !ownedPermissionIds.contains(menu.id())
              ? List.of(menu)
              : all.stream()
                  .filter(button -> Objects.equals(button.parentId(), menu.id()))
                  .filter(button -> !ownedPermissionIds.contains(button.id()) && eligible(button))
                  .toList();
      if (!available.isEmpty()) groups.add(new MenuGroup(menu, available));
    }
    return groups;
  }

  /**
   * 提交申请前检查：权限存在、不属于管理员专属职责、尚未拥有、没有待审核申请、按钮的所属菜单已获得。
   *
   * @param permissionId 所申请的权限 ID
   * @param alreadyPending 申请人是否已有该权限的待审核申请
   * @return 所申请的权限
   * @throws DomainException 权限不存在为 NOT_FOUND，属于管理员职责为 FORBIDDEN，其余不满足为 CONFLICT
   */
  public Permission requireRequestable(long permissionId, boolean alreadyPending) {
    Permission target = permissions.get(permissionId);
    if (target == null) throw new DomainException(NOT_FOUND, "权限不存在。");
    if (!eligible(target))
      throw new DomainException(FORBIDDEN, "该权限属于固定管理员职责，不能通过业务角色申请。");
    if (ownedPermissionIds.contains(target.id())
        || target.parentId() != null && !ownedPermissionIds.contains(target.parentId())
        || alreadyPending)
      throw new DomainException(CONFLICT, "已拥有、已有待审核申请，或尚未获得所属菜单。");
    return target;
  }

  /**
   * 业务应用的权限都可以申请；权限中心只开放用户、授权、审计职责，并且只对管理员开放，
   * 部门维护、应用登记、角色配置等固定管理员职责不能通过业务角色获得。
   */
  private boolean eligible(Permission permission) {
    if (!applications.get(permission.applicationId()).isPermissionCenter()) return true;
    String resource = permission.code().split(":", 2)[0];
    return REQUESTABLE_CENTER_RESOURCES.contains(resource) && applicantRole != ManagementRole.NONE;
  }
}
