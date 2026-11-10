package com.rbac.rbac0.mapper;

import com.rbac.rbac0.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleMapper extends JpaRepository<UserRole, UserRole.Key> {

    boolean existsByRoleId(Long roleId);
}
