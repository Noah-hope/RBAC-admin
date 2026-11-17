package com.rbac.rbac1ddd.domain.role;

import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.CONFLICT;
import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.FORBIDDEN;
import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.INVALID_REQUEST;

import com.rbac.rbac1ddd.domain.permission.PermissionCatalog;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import lombok.Getter;

/**
 * 聚合根：角色。
 *
 * <p>和结构化版本不同，角色不再只是装数据的表记录：它持有自己的直接权限和父角色，
 * 并自己负责"能否创建""能否这样修改""能否删除"等规则（GRASP 信息专家、创建者）。
 * 外部只能通过下面的业务方法改变角色，不能直接改字段。
 *
 * <p>getter 采用 getXxx 命名，供基础设施层的 CloneFactory 生成拷贝代码。
 */
public class Role {
  @Getter
  private Long id;

  @Getter
  private final RoleType type;

  @Getter
  private String name;

  @Getter
  private String description;

  private Set<Long> directPermissionIds;
  private Set<Long> parentRoleIds;

  /**
   * 由仓储重建已有角色，或由工厂方法创建新角色。
   *
   * <p>集合会被复制，外部之后修改传入的集合不会影响聚合。
   *
   * @param id 角色 ID，新角色为 null，保存后由 {@link #assignId} 回填
   * @param name 角色名称
   * @param description 角色说明
   * @param type 角色类型
   * @param directPermissionIds 直接配置的权限 ID
   * @param parentRoleIds 直接父角色 ID
   */
  public Role(
      Long id,
      String name,
      String description,
      RoleType type,
      Set<Long> directPermissionIds,
      Set<Long> parentRoleIds) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.type = type;
    this.directPermissionIds = new TreeSet<>(directPermissionIds);
    this.parentRoleIds = new TreeSet<>(parentRoleIds);
  }

  /**
   * 工厂方法：按配置创建业务角色，规则校验通过后才返回新对象。
   *
   * <p>新角色还没有后代，只需检查自身有效权限（直接 + 继承）中的按钮都有所属菜单。
   *
   * @param config 提交的角色配置
   * @param hierarchy 当前角色继承图
   * @param catalog 权限目录
   * @return 尚未保存、ID 为 null 的业务角色
   * @throws DomainException 配置违反任一规则时为 INVALID_REQUEST
   */
  public static Role createBusiness(
      RoleConfiguration config, RoleHierarchy hierarchy, PermissionCatalog catalog) {
    validateSelection(config, Set.of(), hierarchy, catalog);
    TreeSet<Long> effective = new TreeSet<>(config.permissionIds());
    effective.addAll(hierarchy.permissionSources(config.parentRoleIds()).keySet());
    catalog.ensureMenusIncluded(effective, config.name());
    return new Role(
        null,
        config.name(),
        config.description(),
        RoleType.BUSINESS,
        config.permissionIds(),
        config.parentRoleIds());
  }

  /**
   * 新角色保存后由仓储回填数据库生成的 ID，只能回填一次。
   *
   * @param generatedId 数据库生成的 ID
   * @throws IllegalStateException 角色已有 ID 时，说明调用顺序错误
   */
  public void assignId(long generatedId) {
    if (id != null) throw new IllegalStateException("角色 ID 已存在：" + id);
    id = generatedId;
  }

  /**
   * 返回直接权限的只读视图。
   *
   * <p>手写而不用 Lombok：直接返回内部集合会让外部绕过 {@link #reconfigure} 修改权限，破坏聚合不变式。
   *
   * @return 不可修改的直接权限 ID 集合
   */
  public Set<Long> getDirectPermissionIds() {
    return Collections.unmodifiableSet(directPermissionIds);
  }

  /**
   * 返回直接父角色的只读视图。
   *
   * <p>手写而不用 Lombok：外部修改父角色必须经过 {@link #reconfigure} 的成环和菜单校验。
   *
   * @return 不可修改的父角色 ID 集合
   */
  public Set<Long> getParentRoleIds() {
    return Collections.unmodifiableSet(parentRoleIds);
  }

  /**
   * 判断是否为业务角色。只有业务角色可以修改、删除和参与继承。
   *
   * @return 业务角色时为 true
   */
  public boolean isBusiness() {
    return type == RoleType.BUSINESS;
  }

  /**
   * 预置管理员角色只读。
   *
   * @throws DomainException 不是业务角色时为 FORBIDDEN
   */
  public void ensureEditable() {
    if (!isBusiness()) throw reject(FORBIDDEN, "预置管理角色只读，不能修改或删除。");
  }

  /**
   * 用提交的最终配置替换角色的名称、说明、直接权限和父角色。
   *
   * <p>全部规则先在假设继承图上校验，任何一条失败都抛出异常，角色保持原配置。
   *
   * @param config 提交的最终配置
   * @param hierarchy 当前角色继承图
   * @param catalog 权限目录
   * @return 修改前后的差异
   * @throws DomainException 角色只读时为 FORBIDDEN，配置违反规则时为 INVALID_REQUEST
   */
  public RoleChange reconfigure(
      RoleConfiguration config, RoleHierarchy hierarchy, PermissionCatalog catalog) {
    ensureEditable();
    validateSelection(config, directPermissionIds, hierarchy, catalog);
    if (hierarchy.ancestorsIncluding(config.parentRoleIds()).contains(id))
      throw reject(INVALID_REQUEST, "不能继承自身或形成循环继承。");

    // 修改会影响自身及全部后代的有效权限，逐个检查按钮是否仍有所属菜单。
    RoleHierarchy proposed =
        hierarchy.withConfiguration(id, config.permissionIds(), config.parentRoleIds());
    for (Long affected : hierarchy.descendantsIncluding(id)) {
      catalog.ensureMenusIncluded(
          proposed.effectivePermissionIds(affected), proposed.nameOf(affected));
    }

    RoleChange change =
        new RoleChange(
            name,
            config.name(),
            description,
            config.description(),
            Set.copyOf(directPermissionIds),
            config.permissionIds(),
            Set.copyOf(parentRoleIds),
            config.parentRoleIds());
    name = config.name();
    description = config.description();
    directPermissionIds = new TreeSet<>(config.permissionIds());
    parentRoleIds = new TreeSet<>(config.parentRoleIds());
    return change;
  }

  /**
   * 把权限中心新登记的权限直接授予本角色。
   *
   * <p>只用于总管理员角色：总管理员角色只读，不能通过 {@link #reconfigure} 修改，但必须始终拥有权限中心的全部权限，
   * 否则新登记的管理功能没有人能使用。新权限还没有任何按钮挂在它下面，也不可能被继承重复，因此不需要其他校验。
   *
   * @param permissionId 刚登记的权限 ID
   */
  public void grantNewlyRegisteredPermission(long permissionId) {
    directPermissionIds.add(permissionId);
  }

  /**
   * 删除前检查：不是预置角色、没有用户持有、不参与继承关系。
   *
   * @param assignedUserCount 当前持有该角色的用户数
   * @param hierarchy 当前角色继承图
   * @throws DomainException 预置角色为 FORBIDDEN，仍被持有或仍参与继承为 CONFLICT
   */
  public void ensureDeletable(long assignedUserCount, RoleHierarchy hierarchy) {
    ensureEditable();
    if (assignedUserCount > 0)
      throw reject(CONFLICT, "该角色仍关联 " + assignedUserCount + " 个用户，请先撤销用户关联。");
    if (hierarchy.participatesInInheritance(id))
      throw reject(CONFLICT, "该角色仍存在父角色或子角色，请先解除继承关系。");
  }

  /**
   * 权限必须存在、父角色必须是业务角色、不能新增已通过继承获得的直接权限。
   *
   * <p>修改时原有的直接权限即使与继承重复也允许保留，只拒绝"新增"的重复项，避免历史数据无法保存。
   *
   * @param config 提交的配置
   * @param existingDirect 修改前已有的直接权限；创建时为空集合
   * @param hierarchy 当前角色继承图
   * @param catalog 权限目录
   */
  private static void validateSelection(
      RoleConfiguration config,
      Set<Long> existingDirect,
      RoleHierarchy hierarchy,
      PermissionCatalog catalog) {
    catalog.ensureAllExist(config.permissionIds());
    for (Long parentId : config.parentRoleIds()) {
      if (!hierarchy.isBusinessRole(parentId))
        throw reject(INVALID_REQUEST, "父角色必须是当前存在的业务角色。");
    }
    Set<Long> inherited = hierarchy.permissionSources(config.parentRoleIds()).keySet();
    for (Long permissionId : config.permissionIds()) {
      if (!existingDirect.contains(permissionId) && inherited.contains(permissionId)) {
        throw reject(
            INVALID_REQUEST,
            "权限「" + catalog.get(permissionId).name() + "」已通过继承获得，不能新增重复直接配置。");
      }
    }
  }

  /** 统一构造领域异常，让规则代码只关注原因和提示文字。 */
  private static DomainException reject(DomainException.Reason reason, String detail) {
    return new DomainException(reason, detail);
  }
}
