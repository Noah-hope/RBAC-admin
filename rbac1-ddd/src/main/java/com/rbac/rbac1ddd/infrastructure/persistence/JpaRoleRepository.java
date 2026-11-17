package com.rbac.rbac1ddd.infrastructure.persistence;

import static java.util.stream.Collectors.*;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.rbac.rbac1ddd.domain.role.Role;
import com.rbac.rbac1ddd.domain.role.RoleRepository;
import com.rbac.rbac1ddd.domain.role.RoleType;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.domain.shared.DomainPage;
import com.rbac.rbac1ddd.infrastructure.persistence.dao.RoleDao;
import com.rbac.rbac1ddd.infrastructure.persistence.dao.RoleInheritanceDao;
import com.rbac.rbac1ddd.infrastructure.persistence.dao.RolePermissionDao;
import com.rbac.rbac1ddd.infrastructure.persistence.po.RoleInheritancePo;
import com.rbac.rbac1ddd.infrastructure.persistence.po.RolePermissionPo;
import com.rbac.rbac1ddd.infrastructure.persistence.po.RolePo;
import java.util.Collection;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/**
 * 角色仓储的 JPA 实现：一个角色聚合对应 sys_role、sys_role_permission、sys_role_inheritance 三张表。
 *
 * <p>领域对象 → 持久化对象用 CloneFactory 生成的拷贝方法；持久化对象 → 领域对象通过构造器重建，
 * 领域层不需要为拷贝工具暴露 setter。
 */
@Repository
@RequiredArgsConstructor
class JpaRoleRepository implements RoleRepository {

  /** 角色名称唯一约束名，与 V1__init_rbac1.sql 中的 uq_role_name 一致。 */
  private static final String ROLE_NAME_CONSTRAINT = "uq_role_name";
  private final RoleDao roles;
  private final RolePermissionDao grants;
  private final RoleInheritanceDao inheritance;

  /** 按 ID 加载角色聚合，包括直接权限和父角色。 */
  @Override
  public Optional<Role> findById(long id) {
    return roles.findById(id).map(po -> toDomain(List.of(po)).getFirst());
  }

  /** 按 ID 加悲观写锁加载角色聚合。 */
  @Override
  public Optional<Role> findForUpdate(long id) {
    return roles.findForUpdate(id).map(po -> toDomain(List.of(po)).getFirst());
  }

  /** 按角色类型加载预置管理员角色聚合。 */
  @Override
  public Optional<Role> findByType(RoleType type) {
    return roles
        .findFirstByRoleTypeOrderById(type.name())
        .map(po -> toDomain(List.of(po)).getFirst());
  }

  /** 加载全部角色聚合；关联表各读一次，避免逐个角色查询。 */
  @Override
  public List<Role> findAll() {
    Map<Long, Set<Long>> direct =
        grants.findAll().stream()
            .collect(
                groupingBy(
                    RolePermissionPo::getRoleId,
                    mapping(RolePermissionPo::getPermissionId, toSet())));
    Map<Long, Set<Long>> parents =
        inheritance.findAll().stream()
            .collect(
                groupingBy(
                    RoleInheritancePo::getRoleId,
                    mapping(RoleInheritancePo::getInheritedRoleId, toSet())));
    return roles.findAll().stream().map(po -> toDomain(po, direct, parents)).toList();
  }

  /** 按名称关键字和角色类型分页查询，页码从 1 开始；类型为 null 时不按类型过滤。 */
  @Override
  public DomainPage<Role> search(String keyword, RoleType type, int page, int pageSize) {
    Page<RolePo> found =
        roles.search(
            keyword, type == null ? null : type.name(), PageRequest.of(page - 1, pageSize));
    return new DomainPage<>(toDomain(found.getContent()), found.getTotalElements());
  }

  /**
   * 按聚合整体保存角色：新角色保存后回填 ID，权限和父角色关联只增删发生变化的记录。
   */
  @Override
  public void save(Role role) {
    RolePo po =
        role.getId() == null
            ? newRolePo(role.getType())
            : roles.findById(role.getId()).orElseThrow();
    CloneFactory.copy(po, role);
    saveRoleRow(po);
    if (role.getId() == null) {
      role.assignId(po.getId());
    }

    Set<Long> wantedPermissions = role.getDirectPermissionIds();
    List<RolePermissionPo> currentGrants = grants.findByRoleId(role.getId());
    Set<Long> currentPermissions =
        currentGrants.stream().map(RolePermissionPo::getPermissionId).collect(toSet());
    grants.deleteAll(
        currentGrants.stream()
            .filter(g -> !wantedPermissions.contains(g.getPermissionId()))
            .toList());
    grants.saveAll(
        wantedPermissions.stream()
            .filter(id -> !currentPermissions.contains(id))
            .map(id -> new RolePermissionPo(role.getId(), id))
            .toList());

    Set<Long> wantedParents = role.getParentRoleIds();
    List<RoleInheritancePo> currentLinks = inheritance.findByRoleId(role.getId());
    Set<Long> currentParents =
        currentLinks.stream().map(RoleInheritancePo::getInheritedRoleId).collect(toSet());
    inheritance.deleteAll(
        currentLinks.stream()
            .filter(l -> !wantedParents.contains(l.getInheritedRoleId()))
            .toList());
    inheritance.saveAll(
        wantedParents.stream()
            .filter(id -> !currentParents.contains(id))
            .map(id -> new RoleInheritancePo(role.getId(), id))
            .toList());
  }

  /** 删除角色及其直接权限关联。 */
  @Override
  public void delete(Role role) {
    grants.deleteAll(grants.findByRoleId(role.getId()));
    roles.deleteById(role.getId());
  }

  /**
   * 保存角色行，并把角色名称唯一约束冲突就地转换为业务错误。
   *
   * <p>修改名称时 UPDATE 在事务提交时才执行，因此这里用 saveAndFlush 在当前方法内触发约束检查。
   */
  private void saveRoleRow(RolePo po) {
    try {
      roles.saveAndFlush(po);
    } catch (DataIntegrityViolationException error) {
      String message = String.valueOf(error.getMostSpecificCause().getMessage());
      if (message.toLowerCase(Locale.ROOT).contains(ROLE_NAME_CONSTRAINT)) {
        throw new DomainException(DomainException.Reason.DUPLICATE, "该角色名称已存在。");
      }
      throw error;
    }
  }

  /** 创建新角色行；角色类型只在创建时写入，之后不随 CloneFactory 拷贝改变。 */
  private static RolePo newRolePo(RoleType type) {
    RolePo po = new RolePo();
    po.setRoleType(type.name());
    return po;
  }

  /** 批量重建角色聚合：按角色 ID 一次查出直接权限和父角色关联。 */
  private List<Role> toDomain(Collection<RolePo> pos) {
    List<Long> ids = pos.stream().map(RolePo::getId).toList();
    Map<Long, Set<Long>> direct =
        grants.findByRoleIdIn(ids).stream()
            .collect(
                groupingBy(
                    RolePermissionPo::getRoleId,
                    mapping(RolePermissionPo::getPermissionId, toSet())));
    Map<Long, Set<Long>> parents =
        inheritance.findByRoleIdIn(ids).stream()
            .collect(
                groupingBy(
                    RoleInheritancePo::getRoleId,
                    mapping(RoleInheritancePo::getInheritedRoleId, toSet())));
    return pos.stream().map(po -> toDomain(po, direct, parents)).toList();
  }

  /** 用角色行和预先分组好的关联数据重建单个角色聚合，没有关联时使用空集合。 */
  private static Role toDomain(
      RolePo po, Map<Long, Set<Long>> direct, Map<Long, Set<Long>> parents) {
    return new Role(
        po.getId(),
        po.getName(),
        po.getDescription(),
        RoleType.valueOf(po.getRoleType()),
        direct.getOrDefault(po.getId(), Set.of()),
        parents.getOrDefault(po.getId(), Set.of()));
  }
}
