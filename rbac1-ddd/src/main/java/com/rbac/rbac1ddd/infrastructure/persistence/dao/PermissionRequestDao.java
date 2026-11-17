package com.rbac.rbac1ddd.infrastructure.persistence.dao;

import com.rbac.rbac1ddd.infrastructure.persistence.po.PermissionRequestPo;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** sys_permission_request 表的 Spring Data 访问接口。 */
public interface PermissionRequestDao extends JpaRepository<PermissionRequestPo, Long> {

  /** 查询指定申请人中处于给定状态的全部申请。 */
  List<PermissionRequestPo> findByStatusAndApplicantUserIdIn(
      String status, Collection<Long> applicantUserIds);

  /** 判断申请人是否已有该权限的指定状态申请。 */
  boolean existsByApplicantUserIdAndPermissionIdAndStatus(
      long applicantUserId, long permissionId, String status);

  /** 分页查询本人的申请；status 为 null 时不按状态筛选，排序由分页参数决定。 */
  @Query(
      """
      select request from PermissionRequestPo request
      where request.applicantUserId = :applicantUserId
        and (:status is null or request.status = :status)
      """)
  Page<PermissionRequestPo> searchByApplicant(
      long applicantUserId, String status, Pageable pageable);

  /** 分页查询全部申请；status 为 null 时不按状态筛选。 */
  @Query(
      """
      select request from PermissionRequestPo request
      where (:status is null or request.status = :status)
      """)
  Page<PermissionRequestPo> searchAll(String status, Pageable pageable);

  /** 分页查询申请人当前属于指定部门的申请；status 为 null 时不按状态筛选。 */
  @Query(
      """
      select request from PermissionRequestPo request
      where request.applicantUserId in
            (select account.id from UserPo account where account.departmentId in :departmentIds)
        and (:status is null or request.status = :status)
      """)
  Page<PermissionRequestPo> searchInDepartments(
      Collection<Long> departmentIds, String status, Pageable pageable);
}
