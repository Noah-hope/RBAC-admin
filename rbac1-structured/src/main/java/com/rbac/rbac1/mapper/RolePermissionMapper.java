package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.RolePermission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** sys_role_permission 表的数据访问接口：维护角色直接配置的权限。 */
public interface RolePermissionMapper extends JpaRepository<RolePermission, RolePermission.Key> {

  /**
   * 按权限 ID 升序读取角色的直接权限，用于展示、整体替换和删除角色。
   *
   * @param roleId 角色 ID
   * @return 角色的直接权限关联
   */
  List<RolePermission> findByRoleIdOrderByPermissionIdAsc(long roleId);
}
