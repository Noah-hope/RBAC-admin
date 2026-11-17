package com.rbac.rbac1ddd.domain.permission;

import com.rbac.rbac1ddd.domain.app.BusinessApplication;
import java.util.Set;

/**
 * 已登记的后端接口及其关联的菜单或按钮权限。拥有任一关联权限即可调用。
 *
 * @param id 接口记录 ID
 * @param applicationCode 接口所属应用的编码
 * @param operationId 接口的操作标识，与 API 文档中的 operationId 对应
 * @param permissionIds 允许调用该接口的权限 ID，构造时复制为不可变集合
 */
public record ManagementApi(
    long id, String applicationCode, String operationId, Set<Long> permissionIds) {

  /** 权限中心的自助接口：查看自己的权限、提交和查看自己的权限申请，所有已登录用户都可以调用。 */
  public static final Set<String> SELF_SERVICE_OPERATIONS =
      Set.of(
          "getMyPermissions",
          "getRequestablePermissions",
          "listPermissionRequests",
          "createPermissionRequest",
          "getPermissionRequest",
          "withdrawPermissionRequest");

  /** 复制权限集合，防止外部修改影响已登记的接口授权。 */
  public ManagementApi {
    permissionIds = Set.copyOf(permissionIds);
  }

  /**
   * 判断是否为权限中心的自助接口。
   *
   * @return 属于权限中心且操作标识在自助接口名单中时为 true
   */
  public boolean isSelfService() {
    return BusinessApplication.PERMISSION_CENTER.equals(applicationCode)
        && SELF_SERVICE_OPERATIONS.contains(operationId);
  }

  /**
   * 判断已授予的权限能否调用该接口。
   *
   * <p>自助接口不需要任何权限；其他接口与权限是"或"关系：一个接口可能同时挂在菜单和按钮上，持有其中任意一个即可。
   *
   * @param grantedPermissionIds 调用者的有效权限 ID
   * @return 自助接口，或至少持有一项关联权限时为 true
   */
  public boolean allows(Set<Long> grantedPermissionIds) {
    return isSelfService() || permissionIds.stream().anyMatch(grantedPermissionIds::contains);
  }
}
