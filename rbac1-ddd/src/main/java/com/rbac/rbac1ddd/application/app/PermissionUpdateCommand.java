package com.rbac.rbac1ddd.application.app;

import java.util.List;

/**
 * 修改权限的命令，只修改不为 null 的项。
 *
 * @param name 新名称，null 表示不修改
 * @param code 新标识，null 表示不修改
 * @param apiIds 新的完整接口关联，null 表示保留原关联，空列表表示清空
 */
public record PermissionUpdateCommand(String name, String code, List<Long> apiIds) {

  /** 三项都没有填写时为 true。 */
  public boolean isEmpty() {
    return name == null && code == null && apiIds == null;
  }
}
