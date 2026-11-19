package com.rbac.rbac1ddd.domain.user;

import com.rbac.rbac1ddd.domain.permission.PermissionCatalog;
import com.rbac.rbac1ddd.domain.role.RoleHierarchy;
import com.rbac.rbac1ddd.domain.role.RoleType;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * 聚合根：用户及其持有的角色（业务角色和预置管理员角色）。
 *
 * <p>用户自己计算有效权限、自己完成角色列表的差异替换、资料修改、跨部门调动和部门管理员任免
 * （GRASP 信息专家）。账号只保存密码摘要，摘要算法由应用层的密码端口决定，领域层不关心。
 *
 * <p>字段不设 final（CloneFactory 不拷贝 final 字段），但只提供 getter，外部仍只能通过业务方法改变用户。
 */
public class User {
  /** 首次启动创建的总管理员使用的姓名，与 rbac1-structured 相同。 */
  public static final String FIRST_TOTAL_ADMIN_NAME = "总管理员";

  @Getter
  private Long id;

  @Getter
  private String username;

  @Getter
  private String name;

  @Getter
  private Long departmentId;

  @Getter
  private String passwordHash;

  @Getter
  private UserStatus status;

  private Set<Long> roleIds;

  /**
   * 由仓储重建用户聚合。角色集合会被复制，外部之后修改传入的集合不会影响聚合。
   *
   * @param id 用户 ID，新用户为 null，保存后由 {@link #assignId} 回填
   * @param username 登录账号
   * @param name 姓名
   * @param departmentId 所属部门 ID，可以为 null
   * @param passwordHash 密码摘要
   * @param status 账号状态
   * @param roleIds 持有的全部角色 ID（业务角色和预置管理员角色）
   */
  public User(
      Long id,
      String username,
      String name,
      Long departmentId,
      String passwordHash,
      UserStatus status,
      Set<Long> roleIds) {
    this.id = id;
    this.username = username;
    this.name = name;
    this.departmentId = departmentId;
    this.passwordHash = passwordHash;
    this.status = status;
    this.roleIds = new TreeSet<>(roleIds);
  }

  /**
   * 工厂方法：系统首次启动时创建总管理员账号。
   *
   * <p>姓名固定为「总管理员」，不属于任何部门，账号启用并持有预置的总管理员角色。
   *
   * @param username 登录账号
   * @param passwordHash 初始密码的摘要
   * @param totalAdminRoleId 预置总管理员角色的 ID
   * @return 尚未保存、ID 为 null 的用户
   */
  public static User firstTotalAdmin(String username, String passwordHash, long totalAdminRoleId) {
    return new User(
        null,
        username,
        FIRST_TOTAL_ADMIN_NAME,
        null,
        passwordHash,
        UserStatus.ACTIVE,
        Set.of(totalAdminRoleId));
  }

  /**
   * 工厂方法：管理员新增属于某个部门的用户。新用户没有任何角色，部门管理员身份另行任命。
   *
   * @param username 登录账号
   * @param name 姓名
   * @param departmentId 已确认在操作者管理范围内的所属部门 ID
   * @param passwordHash 初始密码的摘要
   * @param status 初始账号状态
   * @return 尚未保存、ID 为 null 的用户
   */
  public static User create(
      String username, String name, long departmentId, String passwordHash, UserStatus status) {
    return new User(null, username, name, departmentId, passwordHash, status, Set.of());
  }

  /**
   * 新用户保存后由仓储回填数据库生成的 ID，只能回填一次。
   *
   * @param generatedId 数据库生成的 ID
   * @throws IllegalStateException 用户已有 ID 时，说明调用顺序错误
   */
  public void assignId(long generatedId) {
    if (id != null) throw new IllegalStateException("用户 ID 已存在：" + id);
    id = generatedId;
  }

  /**
   * 返回持有角色的只读视图。
   *
   * <p>手写而不用 Lombok：直接返回内部集合会让外部绕过分配、撤销方法修改角色，破坏聚合不变式。
   *
   * @return 不可修改的角色 ID 集合
   */
  public Set<Long> getRoleIds() {
    return Collections.unmodifiableSet(roleIds);
  }

  /**
   * 判断账号是否启用。
   *
   * @return 启用时为 true
   */
  public boolean isActive() {
    return status == UserStatus.ACTIVE;
  }

  /**
   * 用户持有的业务角色，不含预置管理员角色。
   *
   * @param hierarchy 当前角色继承图
   * @return 按 ID 排序的业务角色 ID
   */
  public Set<Long> businessRoleIds(RoleHierarchy hierarchy) {
    return roleIds.stream()
        .filter(hierarchy::isBusinessRole)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  /**
   * 查询启用用户可用于应用角色码映射的业务角色，包含所有可达父角色。
   *
   * @param hierarchy 当前角色继承图
   * @return 去重后的直接和继承业务角色 ID，按 ID 排序
   */
  public Set<Long> effectiveBusinessRoleIds(RoleHierarchy hierarchy) {
    return isActive() ? hierarchy.ancestorsIncluding(businessRoleIds(hierarchy)) : Set.of();
  }

  /**
   * 根据持有的预置管理员角色判断管理身份；同时持有时总管理员优先。
   *
   * @param hierarchy 当前角色继承图
   * @return 管理身份，没有管理员角色时为 NONE
   */
  public ManagementRole managementRole(RoleHierarchy hierarchy) {
    Set<RoleType> types = roleIds.stream().map(hierarchy::typeOf).collect(Collectors.toSet());
    if (types.contains(RoleType.TOTAL_ADMIN)) return ManagementRole.TOTAL_ADMIN;
    if (types.contains(RoleType.DEPARTMENT_ADMIN)) return ManagementRole.DEPARTMENT_ADMIN;
    return ManagementRole.NONE;
  }

  /**
   * 用户有效权限 = 所有角色有效权限的并集；停用账号没有任何权限。
   *
   * @param hierarchy 当前角色继承图
   * @return 有效权限 ID
   */
  public Set<Long> effectivePermissionIds(RoleHierarchy hierarchy) {
    return permissionSources(hierarchy).keySet();
  }

  /**
   * 用户每项有效权限的来源角色：直接配置该权限的角色（持有的角色自身或其祖先），中间传递角色不计为来源。
   *
   * <p>停用账号没有任何权限。
   *
   * @param hierarchy 当前角色继承图
   * @return 按权限 ID 升序排列的「权限 ID → 来源角色 ID」映射
   */
  public Map<Long, Set<Long>> permissionSources(RoleHierarchy hierarchy) {
    return isActive() ? hierarchy.permissionSources(roleIds) : Map.of();
  }

  /**
   * 为指定应用提供有效权限的直接角色，权限可由角色继承获得；停用账号没有任何角色生效。
   *
   * @param applicationId 应用 ID
   * @param hierarchy 当前角色继承图
   * @param catalog 权限目录，用于判断权限所属应用
   * @return 按角色 ID 升序排列的角色 ID，包括预置管理员角色
   */
  public List<Long> rolesGrantingPermissionsIn(
      long applicationId, RoleHierarchy hierarchy, PermissionCatalog catalog) {
    if (!isActive()) return List.of();
    return roleIds.stream()
        .filter(roleId -> catalog.anyIn(applicationId, hierarchy.effectivePermissionIds(roleId)))
        .toList();
  }

  /**
   * 用最终的业务角色列表替换现有业务角色，只新增或解除发生变化的关联；管理员角色保持不变。
   *
   * @param businessRoleIds 已确认全部是业务角色；空集合表示解除全部业务角色
   * @param hierarchy 当前角色继承图，用于区分业务角色和管理员角色
   * @return 实际新增和解除的角色
   */
  public RoleAssignmentChange replaceBusinessRoles(
      Set<Long> businessRoleIds, RoleHierarchy hierarchy) {
    Set<Long> current = businessRoleIds(hierarchy);
    TreeSet<Long> added = new TreeSet<>(businessRoleIds);
    added.removeAll(current);
    TreeSet<Long> removed = new TreeSet<>(current);
    removed.removeAll(businessRoleIds);
    roleIds.removeAll(removed);
    roleIds.addAll(added);
    return new RoleAssignmentChange(added, removed);
  }

  /**
   * 批量分配：只新增尚未持有的业务角色。
   *
   * @param businessRoleIds 已确认全部是业务角色
   * @return 实际新增的角色，解除部分为空
   */
  public RoleAssignmentChange grantBusinessRoles(Set<Long> businessRoleIds) {
    TreeSet<Long> added = new TreeSet<>(businessRoleIds);
    added.removeAll(roleIds);
    roleIds.addAll(added);
    return new RoleAssignmentChange(added, Set.of());
  }

  /**
   * 批量撤销：只解除当前持有的所选业务角色。
   *
   * @param businessRoleIds 已确认全部是业务角色
   * @return 实际解除的角色，新增部分为空
   */
  public RoleAssignmentChange revokeBusinessRoles(Set<Long> businessRoleIds) {
    TreeSet<Long> removed = new TreeSet<>(businessRoleIds);
    removed.retainAll(roleIds);
    roleIds.removeAll(removed);
    return new RoleAssignmentChange(Set.of(), removed);
  }

  /**
   * 修改登录账号。账号唯一性由仓储保存时检查。
   *
   * @param newUsername 新登录账号
   * @return 账号实际发生变化时为 true
   */
  public boolean changeUsername(String newUsername) {
    if (newUsername.equals(username)) return false;
    username = newUsername;
    return true;
  }

  /**
   * 修改姓名。
   *
   * @param newName 新姓名
   * @return 姓名实际发生变化时为 true
   */
  public boolean rename(String newName) {
    if (newName.equals(name)) return false;
    name = newName;
    return true;
  }

  /**
   * 启用或停用账号。停用账号保留角色关联，但不再拥有任何有效权限；撤销会话由应用层完成。
   *
   * @param newStatus 新状态
   * @return 状态实际发生变化时为 true
   */
  public boolean changeStatus(UserStatus newStatus) {
    if (newStatus == status) return false;
    status = newStatus;
    return true;
  }

  /**
   * 跨部门调动。业务角色和管理员角色都保留，只改变所属部门。
   *
   * @param newDepartmentId 已确认存在的目标部门 ID
   * @return 所属部门实际发生变化时为 true
   */
  public boolean transferTo(long newDepartmentId) {
    if (Objects.equals(departmentId, newDepartmentId)) return false;
    departmentId = newDepartmentId;
    return true;
  }

  /**
   * 任命或撤销部门管理员：只增删预置的部门管理员角色，其他角色不变。
   *
   * @param appointed true 表示任命，false 表示撤销
   * @param departmentAdminRoleId 预置部门管理员角色的 ID
   * @return 角色实际发生变化时为 true；已是或已不是部门管理员时为 false
   */
  public boolean changeDepartmentAdministrator(boolean appointed, long departmentAdminRoleId) {
    return appointed ? roleIds.add(departmentAdminRoleId) : roleIds.remove(departmentAdminRoleId);
  }

  /**
   * 判断用户是否持有部门管理员角色。与管理身份不同，同时持有总管理员角色时仍为 true。
   *
   * @param hierarchy 当前角色继承图，用于判断角色类型
   * @return 持有部门管理员角色时为 true
   */
  public boolean isDepartmentAdministrator(RoleHierarchy hierarchy) {
    return roleIds.stream().anyMatch(roleId -> hierarchy.typeOf(roleId) == RoleType.DEPARTMENT_ADMIN);
  }
}
