package com.rbac.rbac0.mapper;

import java.util.List;

import com.rbac.rbac0.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRoleMapper extends JpaRepository<UserRole, UserRole.Key> {

    boolean existsByRoleId(Long roleId);

    long countByRoleId(long roleId);

    @Query("""
            select link from UserRole link, Role role
            where link.userId in :userIds and link.roleId = role.id and role.roleType = 'BUSINESS'
            order by link.userId, link.roleId
            """)
    List<UserRole> findBusinessAssignments(List<Long> userIds);
}
