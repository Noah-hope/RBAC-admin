package com.rbac.rbac1ddd.infrastructure.persistence;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.rbac.rbac1ddd.domain.permission.Permission;
import com.rbac.rbac1ddd.domain.permission.PermissionCatalog;
import com.rbac.rbac1ddd.domain.permission.PermissionDefinition;
import com.rbac.rbac1ddd.domain.permission.PermissionRepository;
import com.rbac.rbac1ddd.domain.permission.PermissionType;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import com.rbac.rbac1ddd.infrastructure.persistence.dao.ApiPermissionDao;
import com.rbac.rbac1ddd.infrastructure.persistence.dao.PermissionDao;
import com.rbac.rbac1ddd.infrastructure.persistence.po.ApiPermissionPo;
import com.rbac.rbac1ddd.infrastructure.persistence.po.PermissionPo;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/**
 * 权限仓储的 JPA 实现：权限目录读取 sys_permission；权限聚合对应 sys_permission 的一行及其 sys_api_permission 关联。
 */
@Repository
@RequiredArgsConstructor
class JpaPermissionRepository implements PermissionRepository {

  /** 应用内权限标识唯一约束名，与 V1__init_rbac1.sql 一致。 */
  private static final String CODE_CONSTRAINT = "uq_permission_application_code";

  private final PermissionDao permissions;
  private final ApiPermissionDao links;

  /** 一次读取全部权限，重建为领域层的权限目录。 */
  @Override
  public PermissionCatalog loadCatalog() {
    return new PermissionCatalog(
        permissions.findAll().stream()
            .map(
                po ->
                    new Permission(
                        po.getId(),
                        po.getApplicationId(),
                        po.getParentId(),
                        po.getName(),
                        po.getCode(),
                        PermissionType.valueOf(po.getType())))
            .toList());
  }

  /** 按 ID 读取权限聚合。 */
  @Override
  public Optional<PermissionDefinition> findById(long id) {
    return permissions.findById(id).map(po -> toDomain(List.of(po)).getFirst());
  }

  /** 读取指定应用下的权限聚合。 */
  @Override
  public Optional<PermissionDefinition> findInApplication(long applicationId, long id) {
    return permissions
        .findByIdAndApplicationId(id, applicationId)
        .map(po -> toDomain(List.of(po)).getFirst());
  }

  /** 读取应用的全部权限聚合，按 ID 升序。 */
  @Override
  public List<PermissionDefinition> findByApplication(long applicationId) {
    return toDomain(permissions.findByApplicationIdOrderByIdAsc(applicationId));
  }

  /** 先从关联表找出权限 ID，再批量重建权限聚合，按 ID 升序。 */
  @Override
  public List<PermissionDefinition> findLinkedToApis(Collection<Long> apiIds) {
    if (apiIds.isEmpty()) return List.of();
    Set<Long> permissionIds =
        links.findByApiIdIn(apiIds).stream().map(ApiPermissionPo::getPermissionId).collect(toSet());
    return toDomain(
        permissions.findAllById(permissionIds).stream()
            .sorted(Comparator.comparing(PermissionPo::getId))
            .toList());
  }

  /**
   * 按聚合整体保存权限：新权限保存后回填 ID，接口关联只增删发生变化的记录。
   *
   * <p>修改标识时 UPDATE 在事务提交时才执行，因此用 saveAndFlush 在当前方法内触发唯一约束检查。
   */
  @Override
  public void save(PermissionDefinition permission) {
    PermissionPo po =
        permission.getId() == null
            ? new PermissionPo()
            : permissions.findById(permission.getId()).orElseThrow();
    CloneFactory.copy(po, permission);
    po.setType(permission.getType().name());
    try {
      permissions.saveAndFlush(po);
    } catch (DataIntegrityViolationException error) {
      if (UniqueConstraints.violated(error, CODE_CONSTRAINT)) {
        throw new DomainException(DomainException.Reason.DUPLICATE, "该应用内已存在相同的权限标识。");
      }
      throw error;
    }
    if (permission.getId() == null) {
      permission.assignId(po.getId());
    }

    Set<Long> wanted = permission.getApiIds();
    List<ApiPermissionPo> current = links.findByPermissionId(permission.getId());
    Set<Long> existing = current.stream().map(ApiPermissionPo::getApiId).collect(toSet());
    links.deleteAll(current.stream().filter(link -> !wanted.contains(link.getApiId())).toList());
    links.saveAll(
        wanted.stream()
            .filter(apiId -> !existing.contains(apiId))
            .map(apiId -> new ApiPermissionPo(permission.getId(), apiId))
            .toList());
  }

  /** 批量重建权限聚合：按权限 ID 一次查出接口关联。 */
  private List<PermissionDefinition> toDomain(List<PermissionPo> pos) {
    if (pos.isEmpty()) return List.of();
    Map<Long, Set<Long>> apiIds =
        links.findByPermissionIdIn(pos.stream().map(PermissionPo::getId).toList()).stream()
            .collect(
                groupingBy(
                    ApiPermissionPo::getPermissionId, mapping(ApiPermissionPo::getApiId, toSet())));
    return pos.stream()
        .map(
            po ->
                new PermissionDefinition(
                    po.getId(),
                    po.getApplicationId(),
                    po.getParentId(),
                    po.getName(),
                    po.getCode(),
                    PermissionType.valueOf(po.getType()),
                    apiIds.getOrDefault(po.getId(), Set.of())))
        .toList();
  }
}
