package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.Permission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PermissionMapper extends JpaRepository<Permission, Long> {

  List<Permission> findByApplicationIdOrderByIdAsc(long applicationId);

  Optional<Permission> findByIdAndApplicationId(long id, long applicationId);

  boolean existsByIdAndApplicationIdAndType(long id, long applicationId, String type);

}
