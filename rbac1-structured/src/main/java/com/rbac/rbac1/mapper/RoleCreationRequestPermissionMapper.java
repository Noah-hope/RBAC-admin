package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.RoleCreationRequestPermission;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** sys_role_create_request_permission 表的数据访问接口：读取角色创建申请中选择的直接权限。 */
public interface RoleCreationRequestPermissionMapper
    extends JpaRepository<RoleCreationRequestPermission, RoleCreationRequestPermission.Key> {

  /**
   * 一次读取多份申请的直接权限，避免列表逐条查询，按权限 ID 排序。
   *
   * @param requestIds 申请 ID
   * @return 这些申请选择的直接权限
   */
  List<RoleCreationRequestPermission> findByRequestIdInOrderByPermissionId(
      Collection<Long> requestIds);
}
