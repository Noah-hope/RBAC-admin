package com.rbac.rbac0.mapper;

import com.rbac.rbac0.entity.ApiPermission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiPermissionMapper extends JpaRepository<ApiPermission, ApiPermission.Key> {}
