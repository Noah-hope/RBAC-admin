package com.rbac.rbac1.dto;

/** 请求校验共用的格式常量。 */
public final class Formats {

  /** 业务 ID 的上限，与前端 JavaScript 能精确表示的最大整数一致。 */
  public static final long MAX_ID = 9_007_199_254_740_991L;

  /** 权限标识支持「资源:动作」以及课程系统的大写字母数字短码。 */
  public static final String PERMISSION_CODE = "(?U)(?:[^:\\s]+:[^:\\s]+|[A-Z][A-Z0-9]*)";

  private Formats() {}
}
