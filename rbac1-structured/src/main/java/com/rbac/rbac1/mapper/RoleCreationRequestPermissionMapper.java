package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.RoleCreationRequestPermission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleCreationRequestPermissionMapper
    extends JpaRepository<RoleCreationRequestPermission, RoleCreationRequestPermission.Key> {
  List<RoleCreationRequestPermission> findByRequestIdOrderByPermissionId(long requestId);
  List<RoleCreationRequestPermission> findByRequestIdInOrderByPermissionId(java.util.Collection<Long> requestIds);
}
