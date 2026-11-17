package com.rbac.rbac1ddd.domain.permission;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 权限仓储接口。领域层只声明需要什么，由基础设施层用 JPA 实现。
 *
 * <p>同一张权限表有两种读法：角色配置和权限检查一次读取完整的 {@link PermissionCatalog}；
 * 登记和修改权限时按 {@link PermissionDefinition} 聚合存取，聚合包含它关联的接口。
 */
public interface PermissionRepository {

  /**
   * 读取全部权限组成权限目录。
   *
   * <p>按钮与菜单的归属校验需要完整目录，因此整体加载。
   *
   * @return 包含全部菜单和按钮的权限目录
   */
  PermissionCatalog loadCatalog();

  /**
   * 按 ID 读取权限聚合，不限应用。登记按钮时用它读取所选的上级权限。
   *
   * @param id 权限 ID
   * @return 权限不存在时为空
   */
  Optional<PermissionDefinition> findById(long id);

  /**
   * 读取指定应用下的权限聚合。
   *
   * @param applicationId 应用 ID
   * @param id 权限 ID
   * @return 权限不存在或不属于该应用时为空
   */
  Optional<PermissionDefinition> findInApplication(long applicationId, long id);

  /**
   * 读取应用的全部权限聚合，用于组装权限树。
   *
   * @param applicationId 应用 ID
   * @return 按 ID 升序排列的菜单和按钮
   */
  List<PermissionDefinition> findByApplication(long applicationId);

  /**
   * 读取关联了任一指定接口的权限聚合，用于展示接口的关联权限和删除接口前解除关联。
   *
   * @param apiIds 接口 ID
   * @return 按 ID 升序排列的权限
   */
  List<PermissionDefinition> findLinkedToApis(Collection<Long> apiIds);

  /**
   * 按聚合整体保存权限：新权限插入并回填 ID，接口关联只增删发生变化的记录。
   *
   * @param permission 权限聚合
   * @throws com.rbac.rbac1ddd.domain.shared.DomainException 同一应用内权限标识重复时为 DUPLICATE
   */
  void save(PermissionDefinition permission);
}
