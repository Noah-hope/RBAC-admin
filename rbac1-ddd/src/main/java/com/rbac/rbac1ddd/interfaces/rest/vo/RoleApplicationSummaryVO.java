package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.role.RoleApplicationSummaryResult;
import java.util.List;

/**
 * 角色在某个业务应用下的有效权限数量。
 *
 * @param applicationId 应用 ID
 * @param applicationName 应用名称
 * @param permissionCount 该应用下的有效权限数
 */
public record RoleApplicationSummaryVO(
    long applicationId, String applicationName, long permissionCount) {

  /** 由应用层结果逐字段转换。 */
  public static RoleApplicationSummaryVO from(RoleApplicationSummaryResult result) {
    return new RoleApplicationSummaryVO(
        result.applicationId(), result.applicationName(), result.permissionCount());
  }

  /** 逐个转换应用汇总列表，保持原有顺序。 */
  static List<RoleApplicationSummaryVO> fromAll(List<RoleApplicationSummaryResult> results) {
    return results.stream().map(RoleApplicationSummaryVO::from).toList();
  }
}
