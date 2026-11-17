package com.rbac.rbac1ddd.domain.role;

import com.rbac.rbac1ddd.domain.shared.DomainPage;
import java.util.List;
import java.util.Optional;

/**
 * 角色仓储接口：以整个聚合（角色 + 直接权限 + 父角色）为单位存取。
 *
 * <p>领域层只依赖这个接口，JPA 实现放在基础设施层（GRASP 间接、受保护变化）。
 */
public interface RoleRepository {

  /**
   * 按 ID 读取角色聚合。
   *
   * @param id 角色 ID
   * @return 角色不存在时为空
   */
  Optional<Role> findById(long id);

  /**
   * 加行锁读取，防止并发修改同一角色。
   *
   * @param id 角色 ID
   * @return 角色不存在时为空
   */
  Optional<Role> findForUpdate(long id);

  /**
   * 按类型读取预置管理员角色，例如首次启动时查找总管理员角色。
   *
   * @param type 角色类型，预置管理员角色每种只有一个
   * @return 该类型的角色不存在时为空
   */
  Optional<Role> findByType(RoleType type);

  /**
   * 读取全部角色，用于构造继承图。
   *
   * @return 全部角色聚合
   */
  List<Role> findAll();

  /**
   * 按名称关键字和类型分页查询，type 为 null 表示不限类型；page 从 1 开始。
   *
   * @param keyword 名称关键字
   * @param type 角色类型，null 表示不限
   * @param page 页码，从 1 开始
   * @param pageSize 每页条数
   * @return 当前页角色和总数
   */
  DomainPage<Role> search(String keyword, RoleType type, int page, int pageSize);

  /**
   * 保存聚合：新角色插入并回填 ID；已有角色只新增或删除发生变化的权限和继承关联。
   *
   * @param role 角色聚合
   * @throws com.rbac.rbac1ddd.domain.shared.DomainException 角色名称已存在时为 DUPLICATE
   */
  void save(Role role);

  /**
   * 删除角色及其权限关联，保留权限定义。
   *
   * @param role 已通过 {@link Role#ensureDeletable} 检查的角色
   */
  void delete(Role role);

  /**
   * 用全部角色构造当前的继承图。
   *
   * <p>成环、后代、有效权限等判断都需要整张图，因此默认整体加载。
   *
   * @return 当前角色继承图
   */
  default RoleHierarchy loadHierarchy() {
    return RoleHierarchy.of(findAll());
  }
}
