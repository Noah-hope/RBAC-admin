package com.rbac.rbac1.dto;

/** 请求校验共用的格式常量。 */
public final class Formats {

  /** 业务 ID 的上限，与前端 JavaScript 能精确表示的最大整数一致。 */
  public static final long MAX_ID = 9_007_199_254_740_991L;

  /** 权限标识格式：「资源:动作」，两段都不含冒号和空白。 */
  public static final String PERMISSION_CODE = "(?U)[^:\\s]+:[^:\\s]+";

  private Formats() {}
}
