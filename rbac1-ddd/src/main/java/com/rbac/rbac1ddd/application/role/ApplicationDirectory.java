package com.rbac.rbac1ddd.application.role;

import java.util.Map;

/** 读模型端口：查询业务应用名称，用于角色详情展示。 */
public interface ApplicationDirectory {
  /**
   * 全部业务应用的名称。
   *
   * @return 应用 ID 到应用名称的映射
   */
  Map<Long, String> namesById();
}
