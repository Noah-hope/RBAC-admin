package com.rbac.rbac1ddd.interfaces.rest;

/** 接口层字符串格式约定，正则与 rbac1-structured 相同。 */
public final class Formats {

  /** 权限编码：冒号分隔的两段，每段不含冒号和空白，例如 course:view。 */
  public static final String PERMISSION_CODE = "(?U)[^:\\s]+:[^:\\s]+";

  /** 应用编码：小写字母开头，只含小写字母、数字、下划线和连字符。 */
  public static final String APPLICATION_CODE = "[a-z][a-z0-9_-]*";

  private Formats() {}
}
