package com.rbac.rbac1.service;

import com.rbac.rbac1.mapper.PermissionRequestMapper;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestStatusService {
  private final PermissionRequestMapper requests;
  private final RolePermissionService effective;

  public RequestStatusService(PermissionRequestMapper requests, RolePermissionService effective) {
    this.requests=requests; this.effective=effective;
  }

  @Transactional
  public void settleUsers(Collection<Long> userIds) {
    if (userIds.isEmpty()) return;
    var pending=requests.findByStatusAndApplicantUserIdIn("PENDING",userIds);
    if (pending.isEmpty()) return;
    var applicants=pending.stream().map(r -> r.applicantUserId).distinct().toList();
    var rights=effective.forUsers(effective.load(),applicants);
    for (var request : pending)
      if (rights.get(request.applicantUserId).sources().containsKey(request.permissionId))
        request.status="SATISFIED";
  }
}
