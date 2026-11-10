package com.rbac.rbac0.mapper;

import java.util.List;

import com.rbac.rbac0.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RolePermissionMapper extends JpaRepository<RolePermission, RolePermission.Key> {

    List<RolePermission> findByRoleIdOrderByPermissionIdAsc(long roleId);

    @Query("""
            select link.roleId as roleId, app.id as applicationId, app.name as applicationName,
                   count(permission.id) as permissionCount
            from RolePermission link, Permission permission, BusinessApplication app
            where link.roleId in :roleIds and link.permissionId = permission.id and permission.applicationId = app.id
            group by link.roleId, app.id, app.name
            order by app.id
            """)
    List<RoleApplicationRow> summarizeApplications(List<Long> roleIds);

    interface RoleApplicationRow {
        long getRoleId();
        long getApplicationId();
        String getApplicationName();
        long getPermissionCount();
    }
}
