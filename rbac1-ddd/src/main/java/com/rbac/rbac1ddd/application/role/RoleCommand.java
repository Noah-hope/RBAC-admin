package com.rbac.rbac1ddd.application.role;

import com.rbac.rbac1ddd.domain.role.RoleConfiguration;
import java.util.List;

/**
 * 创建或修改角色的输入：提交最终的直接权限列表和继承角色列表。
 *
 * @param name 角色名称
 * @param description 角色说明
 * @param permissionIds 最终的直接权限 ID 列表
 * @param inheritedRoleIds 最终的继承（父）角色 ID 列表
 */
public record RoleCommand(
    String name, String description, List<Long> permissionIds, List<Long> inheritedRoleIds) {
  /** 转换为领域层的角色配置值对象。 */
  public RoleConfiguration toConfiguration() {
    return RoleConfiguration.of(name, description, permissionIds, inheritedRoleIds);
  }
}
