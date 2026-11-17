package com.rbac.rbac1ddd.application.app;

/**
 * 登记或修改接口的命令。修改时只修改不为 null 的项。
 *
 * @param operationId operationId
 * @param httpMethod 请求方法
 * @param path 路由模板
 */
public record ApiCommand(String operationId, String httpMethod, String path) {

  /** 三项都没有填写时为 true，只用于修改。 */
  public boolean isEmpty() {
    return operationId == null && httpMethod == null && path == null;
  }
}
