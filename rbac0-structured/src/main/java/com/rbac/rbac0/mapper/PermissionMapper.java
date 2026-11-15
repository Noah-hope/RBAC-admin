package com.rbac.rbac0.mapper;

import java.util.List;
import java.util.Optional;

import com.rbac.rbac0.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PermissionMapper extends JpaRepository<Permission, Long> {

    String EFFECTIVE_FOR_USER = """
            from Permission permission
            where exists (
                select 1 from RolePermission grantLink, UserRole assignment, Role role, UserAccount account
                where grantLink.permissionId = permission.id and grantLink.roleId = assignment.roleId
                  and assignment.roleId = role.id and role.roleType = 'BUSINESS'
                  and assignment.userId = account.id and account.id = :userId and account.status = 'ACTIVE'
            )
            """;

    List<Permission> findByApplicationIdOrderByIdAsc(long applicationId);

    Optional<Permission> findByIdAndApplicationId(long id, long applicationId);

    boolean existsByIdAndApplicationIdAndType(long id, long applicationId, String type);

    @Query("select permission " + EFFECTIVE_FOR_USER + " order by permission.id")
    List<Permission> findEffectiveForUser(long userId);

    @Query("select count(permission.id) > 0 " + EFFECTIVE_FOR_USER
            + " and permission.applicationId = :applicationId and permission.code = :code")
    boolean hasEffectivePermission(long userId, long applicationId, String code);
}
