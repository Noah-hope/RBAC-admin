package com.rbac.rbac0.mapper;

import java.util.List;
import java.util.Optional;

import com.rbac.rbac0.entity.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RoleMapper extends JpaRepository<Role, Long> {

    Role findByRoleType(String roleType);

    Optional<Role> findByIdAndRoleType(long id, String roleType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select role from Role role where role.id = :id and role.roleType = 'BUSINESS'")
    Optional<Role> findBusinessRoleForUpdate(long id);

    @Query("""
            select role from Role role
            where role.roleType = 'BUSINESS' and (:keyword = '' or locate(:keyword, role.name) > 0)
            order by role.id
            """)
    Page<Role> searchBusinessRoles(String keyword, Pageable pageable);

    @Query("""
            select role.roleType from Role role, UserRole assignment
            where assignment.userId = :userId and assignment.roleId = role.id
              and role.roleType in ('TOTAL_ADMIN', 'DEPARTMENT_ADMIN')
            """)
    List<String> findManagementRoles(long userId);

    @Query("""
            select assignment.userId as userId, role.id as id, role.name as name, role.roleType as roleType
            from Role role, UserRole assignment
            where assignment.userId in :userIds and assignment.roleId = role.id
            order by role.id
            """)
    List<UserRoleRow> findAssignedRoles(List<Long> userIds);

    interface UserRoleRow {
        long getUserId();
        long getId();
        String getName();
        String getRoleType();
    }
}
