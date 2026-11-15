package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.PermissionRequest;
import java.util.List;
import org.springframework.data.jpa.repository.*;

public interface PermissionRequestMapper
    extends JpaRepository<PermissionRequest, Long>, JpaSpecificationExecutor<PermissionRequest> {
  boolean existsByApplicantUserIdAndPermissionIdAndStatus(
      long user, long permission, String status);

  List<PermissionRequest> findByStatus(String status);
}
