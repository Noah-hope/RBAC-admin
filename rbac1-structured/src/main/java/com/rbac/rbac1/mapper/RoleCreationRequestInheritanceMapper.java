package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.RoleCreationRequestInheritance;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleCreationRequestInheritanceMapper extends JpaRepository<RoleCreationRequestInheritance, RoleCreationRequestInheritance.Key> {
  java.util.List<RoleCreationRequestInheritance> findByRequestIdOrderByInheritedRoleId(long requestId);
  java.util.List<RoleCreationRequestInheritance> findByRequestIdInOrderByInheritedRoleId(java.util.Collection<Long> requestIds);
}
