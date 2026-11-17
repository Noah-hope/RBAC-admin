package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.user.BatchRoleResult;

/**
 * 批量分配或撤销业务角色的响应体。
 *
 * @param action 操作类型：GRANT 或 REVOKE
 * @param affectedUsers 角色实际发生变化的用户数
 */
public record BatchRoleVO(String action, int affectedUsers) {

  /** 由应用层结果逐字段转换。 */
  public static BatchRoleVO from(BatchRoleResult result) {
    return new BatchRoleVO(result.action(), result.affectedUsers());
  }
}
