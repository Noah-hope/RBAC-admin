package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.RoleCreationRequestInheritance;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** sys_role_create_request_inheritance 表的数据访问接口：读取角色创建申请中选择的父角色快照。 */
public interface RoleCreationRequestInheritanceMapper
    extends JpaRepository<RoleCreationRequestInheritance, RoleCreationRequestInheritance.Key> {

  /**
   * 一次读取多份申请的父角色，避免列表逐条查询，按父角色 ID 排序。
   *
   * @param requestIds 申请 ID
   * @return 这些申请选择的父角色
   */
  List<RoleCreationRequestInheritance> findByRequestIdInOrderByInheritedRoleId(
      Collection<Long> requestIds);
}
