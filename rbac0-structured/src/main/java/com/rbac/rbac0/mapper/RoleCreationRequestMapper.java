package com.rbac.rbac0.mapper;
import com.rbac.rbac0.entity.RoleCreationRequest;
import org.springframework.data.jpa.repository.*;
public interface RoleCreationRequestMapper extends JpaRepository<RoleCreationRequest,Long>,JpaSpecificationExecutor<RoleCreationRequest> {}
