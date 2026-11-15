package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.ApiPermission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiPermissionMapper extends JpaRepository<ApiPermission, ApiPermission.Key> {
  List<ApiPermission> findByApiId(long apiId);

  List<ApiPermission> findByPermissionId(long permissionId);

  void deleteByApiId(long apiId);
}
