package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.RoleCreationRequest;
import org.springframework.data.jpa.repository.*;

public interface RoleCreationRequestMapper
    extends JpaRepository<RoleCreationRequest, Long>,
        JpaSpecificationExecutor<RoleCreationRequest> {}
