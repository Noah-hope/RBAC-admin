package com.rbac.rbac1.service;

import com.rbac.rbac1.mapper.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestStatusService {
  private final PermissionRequestMapper requests;
  private final PermissionMapper permissions;

  public RequestStatusService(PermissionRequestMapper requests, PermissionMapper permissions) {
    this.requests = requests;
    this.permissions = permissions;
  }

  @Transactional
  public void settle() {
    var effective = new HashMap<Long, Set<Long>>();
    for (var request : requests.findByStatus("PENDING")) {
      var ids =
          effective.computeIfAbsent(
              request.applicantUserId,
              id ->
                  permissions.findEffectiveForUser(id).stream()
                      .map(p -> p.id)
                      .collect(Collectors.toSet()));
      if (ids.contains(request.permissionId)) request.status = "SATISFIED";
    }
  }
}
