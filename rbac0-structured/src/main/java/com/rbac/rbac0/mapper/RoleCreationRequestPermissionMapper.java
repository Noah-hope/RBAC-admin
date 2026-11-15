package com.rbac.rbac0.mapper;
import com.rbac.rbac0.entity.RoleCreationRequestPermission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RoleCreationRequestPermissionMapper extends JpaRepository<RoleCreationRequestPermission,RoleCreationRequestPermission.Key> {
    List<RoleCreationRequestPermission> findByRequestIdOrderByPermissionId(long requestId);
}
