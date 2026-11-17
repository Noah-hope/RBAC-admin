package com.rbac.rbac1ddd.domain.request;

import com.rbac.rbac1ddd.domain.shared.DomainPage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 权限申请仓储接口：按申请聚合存取，并支持授权变化后批量结算受影响用户的待审核申请。 */
public interface PermissionRequestRepository {

  /**
   * 按 ID 读取申请。
   *
   * @param id 申请 ID
   * @return 申请不存在时为空
   */
  Optional<PermissionRequest> findById(long id);

  /**
   * 判断申请人是否已有该权限的待审核申请。
   *
   * @param applicantUserId 申请人用户 ID
   * @param permissionId 权限 ID
   * @return 已有待审核申请时为 true
   */
  boolean existsPending(long applicantUserId, long permissionId);

  /**
   * 分页查询本人的申请，按提交时间和 ID 倒序。
   *
   * @param applicantUserId 申请人用户 ID
   * @param status 状态筛选，null 表示全部状态
   * @param page 页码，从 1 开始
   * @param pageSize 每页条数
   * @return 当前页申请和总数
   */
  DomainPage<PermissionRequest> findByApplicant(
      long applicantUserId, RequestStatus status, int page, int pageSize);

  /**
   * 分页查询审核范围内的申请，按提交时间和 ID 倒序。
   *
   * @param applicantDepartmentIds 申请人当前所属部门的范围，null 表示不限部门
   * @param status 状态筛选，null 表示全部状态
   * @param page 页码，从 1 开始
   * @param pageSize 每页条数
   * @return 当前页申请和总数
   */
  DomainPage<PermissionRequest> findForReview(
      Set<Long> applicantDepartmentIds, RequestStatus status, int page, int pageSize);

  /**
   * 查找指定申请人的全部待审核申请。
   *
   * @param applicantUserIds 申请人用户 ID
   * @return 状态为待审核的申请
   */
  List<PermissionRequest> findPendingByApplicants(Collection<Long> applicantUserIds);

  /**
   * 保存申请：新申请插入并回填 ID，已有申请回写状态和审核结果。
   *
   * @param request 申请聚合
   */
  void save(PermissionRequest request);

  /**
   * 只保存申请的状态变化，用于授权变化后的批量结算。
   *
   * @param requests 状态已变化的申请
   */
  void saveStatuses(Collection<PermissionRequest> requests);
}
