package com.rbac.rbac1ddd.domain.request;

import com.rbac.rbac1ddd.domain.shared.DomainPage;
import java.util.Optional;

/** 角色创建申请仓储接口：申请、直接权限和父角色快照作为一个聚合存取。 */
public interface RoleCreationRequestRepository {

  /**
   * 按 ID 读取申请聚合。
   *
   * @param id 申请 ID
   * @return 申请不存在时为空
   */
  Optional<RoleCreationRequest> findById(long id);

  /**
   * 分页查询申请，按提交时间和 ID 倒序。
   *
   * @param applicantUserId 只查该申请人的申请，null 表示全部申请人
   * @param status 状态筛选，null 表示全部状态
   * @param page 页码，从 1 开始
   * @param pageSize 每页条数
   * @return 当前页申请和总数
   */
  DomainPage<RoleCreationRequest> search(
      Long applicantUserId, RequestStatus status, int page, int pageSize);

  /**
   * 保存申请：新申请插入申请、直接权限和父角色快照并回填 ID；已有申请只回写状态和审核结果，
   * 直接权限和父角色快照提交后不再改变。
   *
   * @param request 申请聚合
   */
  void save(RoleCreationRequest request);
}
