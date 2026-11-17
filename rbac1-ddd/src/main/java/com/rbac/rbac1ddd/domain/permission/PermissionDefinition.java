package com.rbac.rbac1ddd.domain.permission;

import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.INVALID_REQUEST;

import com.rbac.rbac1ddd.domain.api.Api;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import lombok.Getter;

/**
 * 聚合根：总管理员在某个应用下登记的菜单或按钮权限，以及它关联的接口。
 *
 * <p>与 {@link Permission} 的分工：{@link Permission} 是角色配置和权限检查使用的只读快照，由
 * {@link PermissionCatalog} 统一提供；本类负责登记和修改权限，自己检查菜单与按钮的上下级规则和接口关联规则
 * （GRASP 信息专家）。权限与接口的多对多关联由权限一方维护，因为关联总是在编辑权限时整体替换。
 *
 * <p>权限 ID、所属应用、类型和所属菜单登记后固定，只能修改名称、标识和关联接口。同一应用内权限标识唯一由数据库约束
 * 保证（权限标识区分大小写，与数据库排序规则一致），保存时由仓储翻译为业务错误。
 *
 * <p>字段不设 final（CloneFactory 不拷贝 final 字段），但只提供 getter，外部只能通过业务方法修改。
 */
public class PermissionDefinition {
  @Getter
  private Long id;

  @Getter
  private Long applicationId;

  @Getter
  private Long parentId;

  @Getter
  private String name;

  @Getter
  private String code;

  @Getter
  private PermissionType type;

  private Set<Long> apiIds;

  /**
   * 由仓储重建已有权限，或由工厂方法创建新权限。集合会被复制。
   *
   * @param id 权限 ID，新权限为 null，保存后由 {@link #assignId} 回填
   * @param applicationId 所属应用 ID
   * @param parentId 按钮所属菜单 ID，菜单为 null
   * @param name 权限名称
   * @param code 权限标识，格式为「资源:操作」
   * @param type 菜单或按钮
   * @param apiIds 关联的接口 ID
   */
  public PermissionDefinition(
      Long id,
      long applicationId,
      Long parentId,
      String name,
      String code,
      PermissionType type,
      Collection<Long> apiIds) {
    this.id = id;
    this.applicationId = applicationId;
    this.parentId = parentId;
    this.name = name;
    this.code = code;
    this.type = type;
    this.apiIds = new TreeSet<>(apiIds);
  }

  /**
   * 工厂方法：在应用下登记菜单或按钮。菜单不能有上级；按钮的上级必须是同一应用的菜单。
   *
   * @param applicationId 已确认存在的应用 ID
   * @param name 权限名称
   * @param code 权限标识
   * @param type 菜单或按钮
   * @param parentId 请求中填写的上级权限 ID，可以为 null
   * @param parent 按 parentId 读到的权限；未填写或不存在时为 null
   * @return 尚未保存、ID 为 null、没有关联接口的权限
   * @throws DomainException 上下级关系不符合规则时为 INVALID_REQUEST
   */
  public static PermissionDefinition register(
      long applicationId,
      String name,
      String code,
      PermissionType type,
      Long parentId,
      PermissionDefinition parent) {
    if (type == PermissionType.MENU) {
      if (parentId != null) throw reject("菜单权限的上级必须为空。");
    } else if (parent == null || !parent.isMenu() || parent.applicationId != applicationId) {
      throw reject("按钮权限的上级必须是同一应用的菜单。");
    }
    return new PermissionDefinition(null, applicationId, parentId, name, code, type, Set.of());
  }

  /**
   * 新权限保存后由仓储回填数据库生成的 ID，只能回填一次。
   *
   * @param generatedId 数据库生成的 ID
   * @throws IllegalStateException 已有 ID 时，说明调用顺序错误
   */
  public void assignId(long generatedId) {
    if (id != null) throw new IllegalStateException("权限 ID 已存在：" + id);
    id = generatedId;
  }

  /**
   * 返回关联接口 ID 的只读视图。
   *
   * <p>手写而不用 Lombok：外部修改关联必须经过 {@link #linkApis} 的同应用校验。
   *
   * @return 按 ID 升序、不可修改的接口 ID 集合
   */
  public Set<Long> getApiIds() {
    return Collections.unmodifiableSet(apiIds);
  }

  /**
   * 是否为菜单。权限树只把菜单作为第一层节点，按钮挂在所属菜单下。
   *
   * @return 菜单时为 true
   */
  public boolean isMenu() {
    return type == PermissionType.MENU;
  }

  /**
   * 用所选接口完整替换关联。所选接口必须都已登记且属于本权限所在的应用；空集合表示清空关联，接口登记本身保留。
   *
   * @param requestedIds 请求中的接口 ID（已去重）
   * @param found 按 requestedIds 读到的已登记接口
   * @throws DomainException 有接口未登记或属于其他应用时为 INVALID_REQUEST
   */
  public void linkApis(Collection<Long> requestedIds, Collection<Api> found) {
    Set<Long> foundIds = new TreeSet<>();
    for (Api api : found) {
      if (!api.belongsTo(applicationId)) throw reject("请选择同一应用下已登记的API。");
      foundIds.add(api.getId());
    }
    if (!foundIds.containsAll(requestedIds)) throw reject("请选择同一应用下已登记的API。");
    apiIds = new TreeSet<>(requestedIds);
  }

  /**
   * 解除与指定接口的关联，用于删除接口登记前清理关联；权限本身保留。
   *
   * @param apiId 接口 ID
   */
  public void unlinkApi(long apiId) {
    apiIds.remove(apiId);
  }

  /**
   * 修改名称。
   *
   * @param newName 新名称
   * @return 名称实际发生变化时为 true
   */
  public boolean rename(String newName) {
    if (newName.equals(name)) return false;
    name = newName;
    return true;
  }

  /**
   * 修改权限标识。
   *
   * @param newCode 新标识
   * @return 标识实际发生变化时为 true
   */
  public boolean recode(String newCode) {
    if (newCode.equals(code)) return false;
    code = newCode;
    return true;
  }

  /** 统一构造"请求不符合要求"的领域异常。 */
  private static DomainException reject(String detail) {
    return new DomainException(INVALID_REQUEST, detail);
  }
}
