package com.rbac.rbac1.service;

import com.rbac.rbac1.entity.PermissionRequest;
import com.rbac.rbac1.mapper.PermissionRequestMapper;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 授权变化后同步待审核权限申请的状态：申请人已经通过其他途径获得申请的权限时，申请自动变为「已满足」。
 */
@Service
@RequiredArgsConstructor
public class RequestStatusService {

  private final PermissionRequestMapper requests;

  private final RolePermissionService effective;

  /**
   * 重新计算指定用户的有效权限，把已经满足的待审核申请标记为 SATISFIED。
   *
   * <p>在触发授权变化的同一事务内调用，使申请状态与授权数据一起提交。
   *
   * @param userIds 授权可能发生变化的用户 ID
   */
  @Transactional
  public void settleUsers(Collection<Long> userIds) {
    if (userIds.isEmpty()) {
      return;
    }
    List<PermissionRequest> pending =
        requests.findByStatusAndApplicantUserIdIn(PermissionRequest.PENDING, userIds);
    if (pending.isEmpty()) {
      return;
    }
    List<Long> applicants = pending.stream().map(r -> r.getApplicantUserId()).distinct().toList();
    Map<Long, RolePermissionService.UserPermissions> rights =
        effective.forUsers(effective.load(), applicants);
    for (PermissionRequest request : pending) {
      if (rights
          .get(request.getApplicantUserId())
          .sources()
          .containsKey(request.getPermissionId())) {
        request.setStatus(PermissionRequest.SATISFIED);
      }
    }
  }
}
