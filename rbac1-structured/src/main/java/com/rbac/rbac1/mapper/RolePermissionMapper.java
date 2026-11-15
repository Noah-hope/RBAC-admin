package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.RolePermission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionMapper extends JpaRepository<RolePermission, RolePermission.Key> {
  List<RolePermission> findByRoleIdOrderByPermissionIdAsc(long roleId);
}
