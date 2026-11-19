package com.rbac.rbac1ddd.interfaces.rest;

/** 接口层字符串格式约定，正则与 rbac1-structured 相同。 */
public final class Formats {

  /** 权限标识支持「资源:动作」以及课程系统的大写字母数字短码。 */
  public static final String PERMISSION_CODE = "(?U)(?:[^:\\s]+:[^:\\s]+|[A-Z][A-Z0-9]*)";

  /** 应用编码：小写字母开头，只含小写字母、数字、下划线和连字符。 */
  public static final String APPLICATION_CODE = "[a-z][a-z0-9_-]*";

  private Formats() {}
}
