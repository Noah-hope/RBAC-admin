package com.rbac.rbac1ddd.interfaces.rest.vo;

import com.rbac.rbac1ddd.application.auth.IdentityDirectoryReader.UserResult;

/** 统一身份用户的最小公开资料。 */
public record IdentityUserVO(String userId, String no, String name) {
  /** 转换目录结果，统一身份 ID 使用与 JWT sub 一致的字符串。 */
  public static IdentityUserVO from(UserResult row) {
    return new IdentityUserVO(Long.toString(row.userId()), row.no(), row.name());
  }
}
