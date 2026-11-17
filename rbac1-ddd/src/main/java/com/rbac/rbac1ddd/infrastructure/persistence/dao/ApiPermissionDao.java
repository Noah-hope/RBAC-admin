package com.rbac.rbac1ddd.infrastructure.persistence.dao;

import com.rbac.rbac1ddd.infrastructure.persistence.po.ApiPermissionPo;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** sys_api_permission 表的 Spring Data 访问接口。 */
public interface ApiPermissionDao extends JpaRepository<ApiPermissionPo, ApiPermissionPo.Key> {

  /** 查询访问指定接口需要的全部权限关联。 */
  List<ApiPermissionPo> findByApiId(long apiId);

  /** 查询一组接口的全部权限关联。 */
  List<ApiPermissionPo> findByApiIdIn(Collection<Long> apiIds);

  /** 查询一项权限关联的全部接口。 */
  List<ApiPermissionPo> findByPermissionId(long permissionId);

  /** 查询一组权限关联的全部接口。 */
  List<ApiPermissionPo> findByPermissionIdIn(Collection<Long> permissionIds);
}
