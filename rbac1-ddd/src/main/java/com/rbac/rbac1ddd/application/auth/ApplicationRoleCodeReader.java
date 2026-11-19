package com.rbac.rbac1ddd.application.auth;

import java.util.Collection;
import java.util.List;

/** 只读端口：查询接入应用为一组业务角色配置的角色码。 */
public interface ApplicationRoleCodeReader {

  /**
   * @param applicationId 目标应用 ID
   * @param roleIds 已确认的直接及继承业务角色 ID
   * @return 去重后按码排序的应用角色码
   */
  List<String> findCodes(long applicationId, Collection<Long> roleIds);
}
