package com.rbac.rbac1ddd.domain.permission;

import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.INVALID_REQUEST;

import com.rbac.rbac1ddd.domain.shared.DomainException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 权限目录：全部菜单和按钮的集合。
 *
 * <p>GRASP 信息专家：只有目录同时知道"有哪些权限"和"按钮属于哪个菜单"，所以由它负责校验。
 */
public final class PermissionCatalog {
  private final Map<Long, Permission> permissions;

  /**
   * 用全部权限构造目录，按 ID 建立不可变索引。
   *
   * @param all 系统中的全部菜单和按钮权限
   */
  public PermissionCatalog(Collection<Permission> all) {
    this.permissions =
        all.stream().collect(Collectors.toUnmodifiableMap(Permission::id, Function.identity()));
  }

  /**
   * 按 ID 查找权限，主要用于拼接错误提示中的权限名称。
   *
   * @param id 权限 ID
   * @return 权限不存在时为 null，调用方应先用 {@link #ensureAllExist} 校验
   */
  public Permission get(long id) {
    return permissions.get(id);
  }

  /**
   * 角色配置中选择的权限必须全部存在。
   *
   * @param ids 所选权限 ID
   * @throws DomainException 有任一权限不存在时为 INVALID_REQUEST
   */
  public void ensureAllExist(Collection<Long> ids) {
    if (!permissions.keySet().containsAll(ids)) throw reject("选择的权限不存在。");
  }

  /**
   * 角色最终拥有的每个按钮，都必须同时拥有它所属的菜单。
   *
   * <p>校验对象是有效权限（直接 + 继承），因为菜单可能来自父角色。
   *
   * @param effectiveIds 角色的有效权限 ID
   * @param roleName 角色名称，用于错误提示
   * @throws DomainException 有按钮缺少所属菜单时为 INVALID_REQUEST
   */
  public void ensureMenusIncluded(Set<Long> effectiveIds, String roleName) {
    for (Long id : effectiveIds) {
      Permission permission = permissions.get(id);
      if (permission.isButton() && !effectiveIds.contains(permission.parentId())) {
        throw reject(
            "角色「"
                + roleName
                + "」的按钮「"
                + permission.name()
                + "」缺少所属菜单「"
                + permissions.get(permission.parentId()).name()
                + "」。");
      }
    }
  }

  /**
   * 列出一组权限中属于指定应用的权限编码，用于当前用户信息和前端权限检查。
   *
   * @param applicationId 应用 ID
   * @param permissionIds 权限 ID，通常是用户的有效权限
   * @return 按权限 ID 升序排列、去重后的权限编码
   */
  public List<String> codesIn(long applicationId, Collection<Long> permissionIds) {
    return permissionIds.stream()
        .sorted()
        .map(permissions::get)
        .filter(permission -> permission.applicationId() == applicationId)
        .map(Permission::code)
        .distinct()
        .toList();
  }

  /**
   * 判断一组权限中是否至少有一项属于指定应用。
   *
   * @param applicationId 应用 ID
   * @param permissionIds 权限 ID
   * @return 有权限属于该应用时为 true
   */
  public boolean anyIn(long applicationId, Collection<Long> permissionIds) {
    return permissionIds.stream()
        .anyMatch(id -> permissions.get(id).applicationId() == applicationId);
  }

  /**
   * 应用登记的全部菜单和按钮，用于角色配置的权限目录。
   *
   * @param applicationId 应用 ID
   * @return 按权限 ID 升序排列的权限；应用没有权限时为空
   */
  public List<Permission> permissionsIn(long applicationId) {
    return permissions.values().stream()
        .filter(permission -> permission.applicationId() == applicationId)
        .sorted(Comparator.comparingLong(Permission::id))
        .toList();
  }

  /**
   * 从一组已获权限中挑出属于指定应用的菜单，用于「我的权限」。
   *
   * @param applicationId 应用 ID
   * @param permissionIds 已获权限 ID，通常是用户的有效权限
   * @return 按权限 ID 升序排列的菜单
   */
  public List<Permission> menusIn(long applicationId, Collection<Long> permissionIds) {
    return permissionIds.stream()
        .sorted()
        .map(permissions::get)
        .filter(permission -> permission.applicationId() == applicationId)
        .filter(permission -> permission.type() == PermissionType.MENU)
        .toList();
  }

  /**
   * 从一组已获权限中挑出挂在指定菜单下的按钮，用于「我的权限」。
   *
   * @param menuId 菜单权限 ID
   * @param permissionIds 已获权限 ID，通常是用户的有效权限
   * @return 按权限 ID 升序排列的按钮
   */
  public List<Permission> buttonsOf(long menuId, Collection<Long> permissionIds) {
    return permissionIds.stream()
        .sorted()
        .map(permissions::get)
        .filter(permission -> permission.parentId() != null && permission.parentId() == menuId)
        .toList();
  }

  /** 统一构造"请求不符合要求"的领域异常。 */
  private static DomainException reject(String detail) {
    return new DomainException(INVALID_REQUEST, detail);
  }
}
