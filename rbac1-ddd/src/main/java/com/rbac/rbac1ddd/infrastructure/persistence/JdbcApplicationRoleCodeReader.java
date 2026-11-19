package com.rbac.rbac1ddd.infrastructure.persistence;

import com.rbac.rbac1ddd.application.auth.ApplicationRoleCodeReader;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 应用角色码查询端口的 JDBC 实现。 */
@Repository
@RequiredArgsConstructor
class JdbcApplicationRoleCodeReader implements ApplicationRoleCodeReader {

  private final JdbcClient jdbc;

  /** 限定应用和业务角色 ID，一次查询得到去重后的角色码。 */
  @Override
  public List<String> findCodes(long applicationId, Collection<Long> roleIds) {
    if (roleIds.isEmpty()) {
      return List.of();
    }
    return jdbc.sql("""
            SELECT DISTINCT role_code
            FROM sys_application_role_code
            WHERE application_id = :applicationId AND role_id IN (:roleIds)
            ORDER BY role_code
            """)
        .param("applicationId", applicationId)
        .param("roleIds", roleIds)
        .query(String.class)
        .list();
  }
}
