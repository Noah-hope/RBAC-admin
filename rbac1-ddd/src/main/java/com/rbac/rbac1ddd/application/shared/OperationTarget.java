package com.rbac.rbac1ddd.application.shared;

import com.rbac.rbac1ddd.domain.api.Api;
import com.rbac.rbac1ddd.domain.app.BusinessApplication;
import com.rbac.rbac1ddd.domain.department.Department;
import com.rbac.rbac1ddd.domain.permission.PermissionDefinition;
import com.rbac.rbac1ddd.domain.request.PermissionRequest;
import com.rbac.rbac1ddd.domain.request.RoleCreationRequest;
import com.rbac.rbac1ddd.domain.role.Role;
import com.rbac.rbac1ddd.domain.user.User;
import lombok.Getter;

/**
 * 操作记录涉及的对象快照。
 *
 * <p>字段不设 final 并提供 getter，供 CloneFactory 拷贝到持久化对象；CloneFactory 不支持 final 字段和
 * record。
 */
@Getter
public class OperationTarget {

  /** 对象类型：角色。 */
  public static final String TYPE_ROLE = "ROLE";

  /** 对象类型：用户。 */
  public static final String TYPE_USER = "USER";

  /** 对象类型：管理接口。 */
  public static final String TYPE_API = "API";

  /** 对象类型：部门。 */
  public static final String TYPE_DEPARTMENT = "DEPARTMENT";

  /** 对象类型：业务应用。 */
  public static final String TYPE_APPLICATION = "APPLICATION";

  /** 对象类型：菜单或按钮权限。 */
  public static final String TYPE_PERMISSION = "PERMISSION";

  /** 对象类型：权限申请，与 rbac1-structured 相同。 */
  public static final String TYPE_PERMISSION_REQUEST = "PERMISSION_REQ";

  /** 对象类型：角色创建申请，与 rbac1-structured 相同。 */
  public static final String TYPE_ROLE_REQUEST = "ROLE_REQUEST";

  private String targetType;
  private Long targetId;
  private String targetName;
  private Long departmentIdAtEvent;

  /**
   * 创建对象快照。
   *
   * @param targetType 对象类型，取本类的 TYPE_* 常量
   * @param targetId 对象 ID，管理接口为空
   * @param targetName 对象名称或接口路径
   * @param departmentIdAtEvent 操作发生时用户所在部门，非用户对象为空
   */
  public OperationTarget(
      String targetType, Long targetId, String targetName, Long departmentIdAtEvent) {
    this.targetType = targetType;
    this.targetId = targetId;
    this.targetName = targetName;
    this.departmentIdAtEvent = departmentIdAtEvent;
  }

  /** 角色快照，不记录部门。 */
  public static OperationTarget role(Role role) {
    return new OperationTarget(TYPE_ROLE, role.getId(), role.getName(), null);
  }

  /** 记录操作发生时用户所在部门，部门管理员按此筛选可见记录。 */
  public static OperationTarget user(User user) {
    return new OperationTarget(TYPE_USER, user.getId(), user.getName(), user.getDepartmentId());
  }

  /**
   * 全局记录中的用户快照，不记录部门，与 rbac1-structured 相同。
   *
   * <p>用于跨部门调动、任免部门管理员这类只有总管理员可见的记录。
   */
  public static OperationTarget globalUser(User user) {
    return new OperationTarget(TYPE_USER, user.getId(), user.getName(), null);
  }

  /** 部门快照，名称取操作完成后的部门名称，不记录部门范围。 */
  public static OperationTarget department(Department department) {
    return new OperationTarget(TYPE_DEPARTMENT, department.getId(), department.getName(), null);
  }

  /** 应用快照，名称取操作完成后的应用名称。 */
  public static OperationTarget application(BusinessApplication application) {
    return new OperationTarget(
        TYPE_APPLICATION, application.getId(), application.getName(), null);
  }

  /** 权限快照，名称取操作完成后的权限名称。 */
  public static OperationTarget permission(PermissionDefinition permission) {
    return new OperationTarget(TYPE_PERMISSION, permission.getId(), permission.getName(), null);
  }

  /** 权限申请快照，名称记为「权限申请#ID」，与 rbac1-structured 相同。 */
  public static OperationTarget permissionRequest(PermissionRequest request) {
    return new OperationTarget(
        TYPE_PERMISSION_REQUEST, request.getId(), "权限申请#" + request.getId(), null);
  }

  /** 角色创建申请快照，名称记为申请的角色名称。 */
  public static OperationTarget roleCreationRequest(RoleCreationRequest request) {
    return new OperationTarget(TYPE_ROLE_REQUEST, request.getId(), request.getRoleName(), null);
  }

  /** 已登记接口的快照，名称记录 operationId，与 rbac1-structured 相同。 */
  public static OperationTarget registeredApi(Api api) {
    return new OperationTarget(TYPE_API, api.getId(), api.getOperationId(), null);
  }

  /** 失败操作记录中的管理接口快照，名称记录接口路由。 */
  public static OperationTarget api(String path) {
    return new OperationTarget(TYPE_API, null, path, null);
  }
}
