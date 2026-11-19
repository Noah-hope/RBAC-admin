package com.rbac.rbac1.mapper;

import com.rbac.rbac1.entity.BusinessApplication;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** sys_application 表的数据访问接口：读取和维护接入权限中心的业务应用。 */
public interface ApplicationMapper extends JpaRepository<BusinessApplication, Long> {

  /**
   * 按唯一的应用标识查找应用，用于登录时校验所选应用和按应用标识检查权限。
   *
   * @param code 应用标识
   * @return 匹配的应用
   */
  Optional<BusinessApplication> findByCode(String code);
}
