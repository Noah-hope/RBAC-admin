package com.rbac.rbac1ddd.application.role;

import com.rbac.rbac1ddd.domain.permission.Permission;
import com.rbac.rbac1ddd.domain.permission.PermissionCatalog;
import com.rbac.rbac1ddd.domain.role.EffectivePermission;
import com.rbac.rbac1ddd.domain.role.Role;
import com.rbac.rbac1ddd.domain.role.RoleHierarchy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 把领域对象组装成角色用例的返回结果。组装只做格式转换，不包含业务规则。
 *
 * <p>权限申请的候选角色和角色创建申请的有效权限预览也使用同样的格式，因此对其他应用服务公开。
 */
@Component
@RequiredArgsConstructor
public class RoleDetailsAssembler {
  private final ApplicationDirectory applications;

  /** 组装角色列表行，有效权限数包含继承得到的权限。 */
  RoleSummaryResult summary(Role role, RoleHierarchy hierarchy, PermissionCatalog catalog) {
    Set<Long> effective = hierarchy.effectivePermissionIds(role.getId());
    return new RoleSummaryResult(
        role.getId(),
        role.getName(),
        role.getDescription(),
        applicationSummaries(effective, catalog),
        role.getType().name(),
        refs(hierarchy, hierarchy.parentsOf(role.getId())),
        refs(hierarchy, hierarchy.childrenOf(role.getId())),
        role.getDirectPermissionIds().size(),
        effective.size());
  }

  /**
   * 组装角色详情，有效权限逐条标明获得方式和来源角色；直接权限 ID 按升序返回。
   *
   * @param role 角色聚合
   * @param hierarchy 当前角色继承图
   * @param catalog 权限目录
   * @param assignedUserCount 持有该角色的用户数
   * @return 角色详情
   */
  public RoleDetailsResult details(
      Role role, RoleHierarchy hierarchy, PermissionCatalog catalog, long assignedUserCount) {
    List<EffectivePermissionResult> effective =
        effectivePermissions(hierarchy.effectivePermissionsOf(role.getId()), hierarchy, catalog);
    Set<Long> effectiveIds =
        effective.stream().map(EffectivePermissionResult::id).collect(Collectors.toSet());
    List<Long> direct = role.getDirectPermissionIds().stream().sorted().toList();
    return new RoleDetailsResult(
        role.getId(),
        role.getName(),
        role.getDescription(),
        applicationSummaries(effectiveIds, catalog),
        role.getType().name(),
        refs(hierarchy, hierarchy.parentsOf(role.getId())),
        refs(hierarchy, hierarchy.childrenOf(role.getId())),
        direct.size(),
        effective.size(),
        direct,
        effective,
        assignedUserCount);
  }

  /**
   * 把有效权限转换为结果，补上权限资料和来源角色名称。
   *
   * @param effective 领域层计算出的有效权限
   * @param hierarchy 当前角色继承图，提供来源角色名称
   * @param catalog 权限目录
   * @return 顺序不变的有效权限结果
   */
  public static List<EffectivePermissionResult> effectivePermissions(
      List<EffectivePermission> effective, RoleHierarchy hierarchy, PermissionCatalog catalog) {
    return effective.stream()
        .map(
            e -> {
              Permission p = catalog.get(e.permissionId());
              return new EffectivePermissionResult(
                  p.id(),
                  p.applicationId(),
                  p.name(),
                  p.code(),
                  p.type().name(),
                  p.parentId(),
                  e.acquisition().name(),
                  refs(hierarchy, e.sourceRoleIds()));
            })
        .toList();
  }

  /** 按应用统计权限数量，结果按应用 ID 升序排列。 */
  private List<RoleApplicationSummaryResult> applicationSummaries(
      Set<Long> permissionIds, PermissionCatalog catalog) {
    Map<Long, String> names = applications.namesById();
    Map<Long, Long> counts = new TreeMap<>();
    permissionIds.forEach(id -> counts.merge(catalog.get(id).applicationId(), 1L, Long::sum));
    return counts.entrySet().stream()
        .map(e -> new RoleApplicationSummaryResult(e.getKey(), names.get(e.getKey()), e.getValue()))
        .toList();
  }

  /** 把角色 ID 转换为按 ID 升序排列的角色引用。 */
  static List<RoleRefResult> refs(RoleHierarchy hierarchy, Collection<Long> ids) {
    return RoleRefResult.listOf(hierarchy, ids);
  }
}
