package com.rbac.rbac1ddd.domain.api;

import lombok.Getter;

/**
 * 聚合根：业务应用登记的一个后端接口（operationId、请求方法和路由模板）。
 *
 * <p>接口与菜单、按钮权限是多对多关系，关联由权限一方维护（见
 * {@link com.rbac.rbac1ddd.domain.permission.PermissionDefinition}）：移除权限的关联不会删除接口登记，
 * 删除接口登记时由应用服务先让关联它的权限解除关联。同一应用内 operationId 唯一、请求方法与路由的组合唯一，
 * 由数据库约束保证，保存时由仓储翻译为业务错误。
 *
 * <p>字段不设 final（CloneFactory 不拷贝 final 字段），但只提供 getter，外部只能通过业务方法修改。
 */
@Getter
public class Api {
  private Long id;
  private Long applicationId;
  private String operationId;
  private String httpMethod;
  private String path;

  /**
   * 由仓储重建接口登记，或由工厂方法创建新登记。
   *
   * @param id 接口 ID，新登记为 null，保存后由 {@link #assignId} 回填
   * @param applicationId 所属应用 ID，登记后不变
   * @param operationId 业务系统 API 文档中的 operationId
   * @param httpMethod 请求方法，例如 GET、POST
   * @param path 路由模板，例如 /courses/{courseId}
   */
  public Api(Long id, long applicationId, String operationId, String httpMethod, String path) {
    this.id = id;
    this.applicationId = applicationId;
    this.operationId = operationId;
    this.httpMethod = httpMethod;
    this.path = path;
  }

  /**
   * 工厂方法：在应用下登记新接口。
   *
   * @param applicationId 已确认存在的应用 ID
   * @param operationId operationId
   * @param httpMethod 请求方法
   * @param path 路由模板
   * @return 尚未保存、ID 为 null 的接口登记
   */
  public static Api register(long applicationId, String operationId, String httpMethod, String path) {
    return new Api(null, applicationId, operationId, httpMethod, path);
  }

  /**
   * 新登记保存后由仓储回填数据库生成的 ID，只能回填一次。
   *
   * @param generatedId 数据库生成的 ID
   * @throws IllegalStateException 已有 ID 时，说明调用顺序错误
   */
  public void assignId(long generatedId) {
    if (id != null) throw new IllegalStateException("接口 ID 已存在：" + id);
    id = generatedId;
  }

  /**
   * 修改接口资料，只修改不为 null 的项，所属应用不变。
   *
   * @param newOperationId 新的 operationId，null 表示不修改
   * @param newHttpMethod 新的请求方法，null 表示不修改
   * @param newPath 新的路由模板，null 表示不修改
   */
  public void revise(String newOperationId, String newHttpMethod, String newPath) {
    if (newOperationId != null) operationId = newOperationId;
    if (newHttpMethod != null) httpMethod = newHttpMethod;
    if (newPath != null) path = newPath;
  }

  /**
   * 判断接口是否属于指定应用。权限只能关联同一应用的接口。
   *
   * @param otherApplicationId 应用 ID
   * @return 属于该应用时为 true
   */
  public boolean belongsTo(long otherApplicationId) {
    return applicationId == otherApplicationId;
  }

  /**
   * 「请求方法 路由」形式的接口描述，写入操作记录。
   *
   * @return 例如 {@code GET /courses}
   */
  public String route() {
    return httpMethod + " " + path;
  }
}
