package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.PermissionRequest;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** sys_permission_request 表的数据访问接口；列表的组合筛选条件由 Service 用 Specification 拼装。 */
public interface PermissionRequestMapper
    extends JpaRepository<PermissionRequest, Long>, JpaSpecificationExecutor<PermissionRequest> {

  /**
   * 判断用户对同一权限是否已有指定状态的申请，用于阻止重复提交待审核申请。
   *
   * @param user 申请人 ID
   * @param permission 权限 ID
   * @param status 申请状态
   * @return 已存在时为 true
   */
  boolean existsByApplicantUserIdAndPermissionIdAndStatus(
      long user, long permission, String status);

  /**
   * 查询一批用户处于指定状态的申请；用户权限变化后据此把已满足的待审核申请自动结束。
   *
   * @param status 申请状态
   * @param userIds 申请人 ID
   * @return 匹配的申请
   */
  List<PermissionRequest> findByStatusAndApplicantUserIdIn(
      String status, Collection<Long> userIds);
}
