package com.rbac.rbac0.mapper;

import java.util.List;

import com.rbac.rbac0.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoleMapper extends JpaRepository<Role, Long> {

    Role findByRoleType(String roleType);

    @Query("""
            select role.roleType from Role role, UserRole assignment
            where assignment.userId = :userId and assignment.roleId = role.id
              and role.roleType in ('TOTAL_ADMIN', 'DEPARTMENT_ADMIN')
            """)
    List<String> findManagementRoles(long userId);
}
