package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.RoleCreationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** sys_role_create_request 表的数据访问接口；列表的组合筛选条件由 Service 用 Specification 拼装。 */
public interface RoleCreationRequestMapper
    extends JpaRepository<RoleCreationRequest, Long>,
        JpaSpecificationExecutor<RoleCreationRequest> {}
