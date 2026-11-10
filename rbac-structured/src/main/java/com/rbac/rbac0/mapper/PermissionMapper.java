package com.rbac.rbac0.mapper;

import java.util.List;
import java.util.Optional;

import com.rbac.rbac0.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionMapper extends JpaRepository<Permission, Long> {

    List<Permission> findByApplicationIdOrderByIdAsc(long applicationId);

    Optional<Permission> findByIdAndApplicationId(long id, long applicationId);

    boolean existsByIdAndApplicationIdAndType(long id, long applicationId, String type);
}
