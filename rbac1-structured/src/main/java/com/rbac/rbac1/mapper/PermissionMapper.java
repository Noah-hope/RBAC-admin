package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.Permission;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** sys_permission 表的数据访问接口：按应用读取菜单和按钮权限。 */
public interface PermissionMapper extends JpaRepository<Permission, Long> {

  /**
   * 按 ID 升序读取应用的全部权限，用于组装应用的菜单—按钮权限树。
   *
   * @param applicationId 应用 ID
   * @return 该应用的全部权限
   */
  List<Permission> findByApplicationIdOrderByIdAsc(long applicationId);

  /**
   * 按 ID 查找指定应用下的权限；权限不属于该应用时视为不存在，防止跨应用修改。
   *
   * @param id 权限 ID
   * @param applicationId 所属应用 ID
   * @return 属于该应用的权限
   */
  Optional<Permission> findByIdAndApplicationId(long id, long applicationId);

  /**
   * 判断某个权限是否为同一应用下指定类型的权限，用于校验按钮的上级必须是本应用的菜单。
   *
   * @param id 权限 ID
   * @param applicationId 应用 ID
   * @param type 权限类型
   * @return 存在且类型匹配时为 true
   */
  boolean existsByIdAndApplicationIdAndType(long id, long applicationId, String type);
}
