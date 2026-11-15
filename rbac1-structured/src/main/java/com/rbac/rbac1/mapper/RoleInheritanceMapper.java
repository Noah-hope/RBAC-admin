package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.RoleInheritance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleInheritanceMapper extends JpaRepository<RoleInheritance, RoleInheritance.Key> {
  boolean existsByRoleIdOrInheritedRoleId(long roleId, long inheritedRoleId);
}
