package com.rbac.rbac1ddd.infrastructure.persistence.dao;

import com.rbac.rbac1ddd.infrastructure.persistence.po.RoleCreationRequestPo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** sys_role_create_request 表的 Spring Data 访问接口。 */
public interface RoleCreationRequestDao extends JpaRepository<RoleCreationRequestPo, Long> {

  /** 分页查询申请；申请人或状态为 null 时不按该条件筛选，排序由分页参数决定。 */
  @Query(
      """
      select request from RoleCreationRequestPo request
      where (:applicantUserId is null or request.applicantUserId = :applicantUserId)
        and (:status is null or request.status = :status)
      """)
  Page<RoleCreationRequestPo> search(Long applicantUserId, String status, Pageable pageable);
}
