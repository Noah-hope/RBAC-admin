package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.ApiPermission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** sys_api_permission 表的数据访问接口：维护权限与接口的多对多关联。 */
public interface ApiPermissionMapper extends JpaRepository<ApiPermission, ApiPermission.Key> {

  /**
   * 查询关联到某个接口的全部权限，用于判断用户是否可以调用该接口。
   *
   * @param apiId 接口 ID
   * @return 该接口的权限关联
   */
  List<ApiPermission> findByApiId(long apiId);

  /**
   * 查询某个权限关联的全部接口，用于展示权限详情和整体替换关联。
   *
   * @param permissionId 权限 ID
   * @return 该权限的接口关联
   */
  List<ApiPermission> findByPermissionId(long permissionId);

  /**
   * 删除接口前先解除它与所有权限的关联。
   *
   * @param apiId 接口 ID
   */
  void deleteByApiId(long apiId);
}
