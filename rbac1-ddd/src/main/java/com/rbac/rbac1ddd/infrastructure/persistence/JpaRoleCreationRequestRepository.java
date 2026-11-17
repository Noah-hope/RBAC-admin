package com.rbac.rbac1ddd.infrastructure.persistence;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.rbac.rbac1ddd.domain.request.ParentRoleSnapshot;
import com.rbac.rbac1ddd.domain.request.RequestStatus;
import com.rbac.rbac1ddd.domain.request.RoleCreationRequest;
import com.rbac.rbac1ddd.domain.request.RoleCreationRequestRepository;
import com.rbac.rbac1ddd.domain.shared.DomainPage;
import com.rbac.rbac1ddd.infrastructure.persistence.dao.RoleCreationRequestDao;
import com.rbac.rbac1ddd.infrastructure.persistence.dao.RoleCreationRequestInheritanceDao;
import com.rbac.rbac1ddd.infrastructure.persistence.dao.RoleCreationRequestPermissionDao;
import com.rbac.rbac1ddd.infrastructure.persistence.po.RoleCreationRequestInheritancePo;
import com.rbac.rbac1ddd.infrastructure.persistence.po.RoleCreationRequestPermissionPo;
import com.rbac.rbac1ddd.infrastructure.persistence.po.RoleCreationRequestPo;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/**
 * 角色创建申请仓储的 JPA 实现：一个申请聚合对应 sys_role_create_request、
 * sys_role_create_request_permission、sys_role_create_request_inheritance 三张表。
 */
@Repository
@RequiredArgsConstructor
class JpaRoleCreationRequestRepository implements RoleCreationRequestRepository {
  private final RoleCreationRequestDao requests;
  private final RoleCreationRequestPermissionDao permissions;
  private final RoleCreationRequestInheritanceDao parents;

  /** 按 ID 读取申请聚合。 */
  @Override
  public Optional<RoleCreationRequest> findById(long id) {
    return requests.findById(id).map(po -> toDomain(List.of(po)).getFirst());
  }

  /** 分页查询申请，按提交时间和 ID 倒序。 */
  @Override
  public DomainPage<RoleCreationRequest> search(
      Long applicantUserId, RequestStatus status, int page, int pageSize) {
    Page<RoleCreationRequestPo> found =
        requests.search(
            applicantUserId,
            status == null ? null : status.name(),
            PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    return new DomainPage<>(toDomain(found.getContent()), found.getTotalElements());
  }

  /** 新申请连同直接权限和父角色快照一起插入；已有申请只回写状态和审核结果。 */
  @Override
  public void save(RoleCreationRequest request) {
    boolean created = request.getId() == null;
    RoleCreationRequestPo po =
        created ? new RoleCreationRequestPo() : requests.findById(request.getId()).orElseThrow();
    CloneFactory.copy(po, request);
    po.setStatus(request.getStatus().name());
    requests.save(po);
    if (!created) return;
    request.assignId(po.getId());
    permissions.saveAll(
        request.getPermissionIds().stream()
            .map(id -> new RoleCreationRequestPermissionPo(po.getId(), id))
            .toList());
    parents.saveAll(
        request.getParentRoles().stream()
            .map(p -> new RoleCreationRequestInheritancePo(po.getId(), p.roleId(), p.name()))
            .toList());
  }

  /** 批量重建申请聚合：按申请 ID 一次查出直接权限和父角色快照。 */
  private List<RoleCreationRequest> toDomain(Collection<RoleCreationRequestPo> pos) {
    List<Long> ids = pos.stream().map(RoleCreationRequestPo::getId).toList();
    Map<Long, Set<Long>> direct =
        permissions.findByRequestIdIn(ids).stream()
            .collect(
                groupingBy(
                    RoleCreationRequestPermissionPo::getRequestId,
                    mapping(RoleCreationRequestPermissionPo::getPermissionId, toSet())));
    Map<Long, List<ParentRoleSnapshot>> snapshots =
        parents.findByRequestIdIn(ids).stream()
            .collect(
                groupingBy(
                    RoleCreationRequestInheritancePo::getRequestId,
                    mapping(
                        link ->
                            new ParentRoleSnapshot(
                                link.getInheritedRoleId(), link.getInheritedRoleName()),
                        toList())));
    return pos.stream()
        .map(
            po ->
                new RoleCreationRequest(
                    po.getId(),
                    po.getApplicantUserId(),
                    po.getRoleName(),
                    po.getRoleDescription(),
                    direct.getOrDefault(po.getId(), Set.of()),
                    snapshots.getOrDefault(po.getId(), List.of()),
                    RequestStatus.valueOf(po.getStatus()),
                    po.getReviewerUserId(),
                    po.getCreatedRoleId(),
                    po.getCreatedAt(),
                    po.getReviewedAt()))
        .toList();
  }
}
