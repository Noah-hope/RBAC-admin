package com.rbac.rbac1ddd.interfaces.rest;

/**
 * 接口层 ID 取值约定。
 *
 * <p>前端用 JavaScript 处理 ID，最大值限制为 Number.MAX_SAFE_INTEGER，与 rbac1-structured 一致。
 */
public final class Ids {

  /** 允许的最大 ID。 */
  public static final long MAX_ID = 9_007_199_254_740_991L;

  private Ids() {}
}
