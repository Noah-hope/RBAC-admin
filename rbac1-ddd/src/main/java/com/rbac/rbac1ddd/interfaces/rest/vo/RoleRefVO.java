package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.role.RoleRefResult;
import java.util.List;

/**
 * 角色引用（RoleRef），用于列出继承关系和权限来源。
 *
 * @param id 角色 ID
 * @param name 角色名称
 */
public record RoleRefVO(long id, String name) {

  /** 由应用层结果逐字段转换。 */
  public static RoleRefVO from(RoleRefResult result) {
    return new RoleRefVO(result.id(), result.name());
  }

  /** 逐个转换角色引用列表，保持原有顺序。 */
  static List<RoleRefVO> fromAll(List<RoleRefResult> results) {
    return results.stream().map(RoleRefVO::from).toList();
  }
}
