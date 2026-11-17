package com.rbac.rbac1ddd.infrastructure.persistence;

import cn.edu.xmu.clonefactory.util.CloneFactory;
import com.rbac.rbac1ddd.domain.request.PermissionRequest;
import com.rbac.rbac1ddd.domain.request.PermissionRequestRepository;
import com.rbac.rbac1ddd.domain.request.RequestStatus;
import com.rbac.rbac1ddd.domain.shared.DomainPage;
import com.rbac.rbac1ddd.infrastructure.persistence.dao.PermissionRequestDao;
import com.rbac.rbac1ddd.infrastructure.persistence.po.PermissionRequestPo;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** 权限申请仓储的 JPA 实现：读写 sys_permission_request。 */
@Repository
@RequiredArgsConstructor
class JpaPermissionRequestRepository implements PermissionRequestRepository {
  private final PermissionRequestDao requests;

  /** 按 ID 读取申请。 */
  @Override
  public Optional<PermissionRequest> findById(long id) {
    return requests.findById(id).map(JpaPermissionRequestRepository::toDomain);
  }

  /** 判断申请人是否已有该权限的待审核申请。 */
  @Override
  public boolean existsPending(long applicantUserId, long permissionId) {
    return requests.existsByApplicantUserIdAndPermissionIdAndStatus(
        applicantUserId, permissionId, RequestStatus.PENDING.name());
  }

  /** 分页查询本人的申请，按提交时间和 ID 倒序。 */
  @Override
  public DomainPage<PermissionRequest> findByApplicant(
      long applicantUserId, RequestStatus status, int page, int pageSize) {
    return toPage(requests.searchByApplicant(applicantUserId, name(status), newestFirst(page, pageSize)));
  }

  /** 分页查询待审核范围内的申请，按提交时间和 ID 倒序；部门为 null 表示不限部门。 */
  @Override
  public DomainPage<PermissionRequest> findForReview(
      Set<Long> applicantDepartmentIds, RequestStatus status, int page, int pageSize) {
    Pageable pageable = newestFirst(page, pageSize);
    return toPage(
        applicantDepartmentIds == null
            ? requests.searchAll(name(status), pageable)
            : requests.searchInDepartments(applicantDepartmentIds, name(status), pageable));
  }

  /** 查询指定申请人的全部待审核申请。 */
  @Override
  public List<PermissionRequest> findPendingByApplicants(Collection<Long> applicantUserIds) {
    return requests
        .findByStatusAndApplicantUserIdIn(RequestStatus.PENDING.name(), applicantUserIds)
        .stream()
        .map(JpaPermissionRequestRepository::toDomain)
        .toList();
  }

  /** 新申请插入并回填 ID；已有申请整体回写，由事务提交时统一更新。 */
  @Override
  public void save(PermissionRequest request) {
    PermissionRequestPo po =
        request.getId() == null
            ? new PermissionRequestPo()
            : requests.findById(request.getId()).orElseThrow();
    CloneFactory.copy(po, request);
    po.setStatus(request.getStatus().name());
    requests.save(po);
    if (request.getId() == null) {
      request.assignId(po.getId());
    }
  }

  /** 只回写申请状态；修改托管实体，由事务提交时统一更新。 */
  @Override
  public void saveStatuses(Collection<PermissionRequest> changed) {
    Map<Long, PermissionRequest> byId =
        changed.stream()
            .collect(Collectors.toMap(PermissionRequest::getId, Function.identity()));
    requests
        .findAllById(byId.keySet())
        .forEach(po -> po.setStatus(byId.get(po.getId()).getStatus().name()));
  }

  /** 列表排序与 rbac1-structured 相同：提交时间倒序，同一时间按 ID 倒序。 */
  private static Pageable newestFirst(int page, int pageSize) {
    return PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
  }

  /** 状态筛选条件，null 表示不筛选。 */
  private static String name(RequestStatus status) {
    return status == null ? null : status.name();
  }

  /** Spring Data 分页 → 领域分页。 */
  private static DomainPage<PermissionRequest> toPage(Page<PermissionRequestPo> found) {
    return new DomainPage<>(
        found.getContent().stream().map(JpaPermissionRequestRepository::toDomain).toList(),
        found.getTotalElements());
  }

  /** 持久化对象 → 领域对象。 */
  private static PermissionRequest toDomain(PermissionRequestPo po) {
    return new PermissionRequest(
        po.getId(),
        po.getApplicantUserId(),
        po.getPermissionId(),
        po.getReason(),
        RequestStatus.valueOf(po.getStatus()),
        po.getReviewerUserId(),
        po.getApprovedRoleId(),
        po.getCreatedAt(),
        po.getReviewedAt());
  }
}
