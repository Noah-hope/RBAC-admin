package com.rbac.rbac1ddd.application.request;

import com.rbac.rbac1ddd.domain.request.PermissionRequest;
import com.rbac.rbac1ddd.domain.request.PermissionRequestRepository;
import com.rbac.rbac1ddd.domain.role.RoleHierarchy;
import com.rbac.rbac1ddd.domain.role.RoleRepository;
import com.rbac.rbac1ddd.domain.user.User;
import com.rbac.rbac1ddd.domain.user.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 授权变化后，把申请人已经通过继承或直接授权拥有的待审核申请标记为"已满足"。 */
@Component
@RequiredArgsConstructor
public class PermissionRequestSettlement {
  private final PermissionRequestRepository requests;
  private final UserRepository users;
  private final RoleRepository roles;

  /**
   * 结算受影响用户的待审核申请。
   *
   * <p>在调用方事务中执行，读取的是本次修改之后的授权；只保存状态发生变化的申请。
   *
   * @param userIds 授权发生变化的用户 ID
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void settle(Collection<Long> userIds) {
    if (userIds.isEmpty()) return;
    List<PermissionRequest> pending = requests.findPendingByApplicants(userIds);
    if (pending.isEmpty()) return;
    RoleHierarchy hierarchy = roles.loadHierarchy();
    Map<Long, User> applicants =
        users
            .findAllById(
                pending.stream().map(PermissionRequest::getApplicantUserId).distinct().toList())
            .stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
    List<PermissionRequest> settled =
        pending.stream()
            .filter(
                request ->
                    request.settleWith(
                        applicants
                            .get(request.getApplicantUserId())
                            .effectivePermissionIds(hierarchy)))
            .toList();
    if (!settled.isEmpty()) {
      requests.saveStatuses(settled);
    }
  }
}
