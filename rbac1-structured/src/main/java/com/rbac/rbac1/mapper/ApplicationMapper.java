package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.BusinessApplication;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** sys_application 表的数据访问接口：读取和维护接入权限中心的业务应用。 */
public interface ApplicationMapper extends JpaRepository<BusinessApplication, Long> {

  /**
   * 按唯一的应用标识查找应用，用于登录时校验所选应用和按应用标识检查权限。
   *
   * @param code 应用标识
   * @return 匹配的应用
   */
  Optional<BusinessApplication> findByCode(String code);

  /**
   * 查找目标应用为指定业务角色配置的角色码，去重并按码排序。
   *
   * @param applicationId 目标应用 ID
   * @param roleIds 已确认的直接及继承业务角色 ID，非空
   * @return 应用角色码
   */
  @Query(
      value = """
          SELECT DISTINCT role_code
          FROM sys_application_role_code
          WHERE application_id = :applicationId AND role_id IN (:roleIds)
          ORDER BY role_code
          """,
      nativeQuery = true)
  List<String> findRoleCodes(
      @Param("applicationId") long applicationId, @Param("roleIds") Collection<Long> roleIds);
}
