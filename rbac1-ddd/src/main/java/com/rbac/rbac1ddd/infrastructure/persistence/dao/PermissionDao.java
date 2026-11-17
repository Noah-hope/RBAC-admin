package com.rbac.rbac1ddd.infrastructure.persistence.dao;

import com.rbac.rbac1ddd.infrastructure.persistence.po.PermissionPo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** sys_permission 表的 Spring Data 访问接口。 */
public interface PermissionDao extends JpaRepository<PermissionPo, Long> {

  /** 查询指定应用下的权限。 */
  Optional<PermissionPo> findByIdAndApplicationId(long id, long applicationId);

  /** 按 ID 升序查询应用的全部权限。 */
  List<PermissionPo> findByApplicationIdOrderByIdAsc(long applicationId);
}
