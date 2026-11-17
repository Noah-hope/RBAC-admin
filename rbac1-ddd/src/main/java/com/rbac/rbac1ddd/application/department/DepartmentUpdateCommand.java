package com.rbac.rbac1ddd.application.department;

/**
 * 修改部门的命令。上级部门可以显式改为 null（顶级），因此单独记录请求中是否出现了 parentId。
 *
 * @param name 新名称，null 表示不修改
 * @param parentProvided 请求中是否出现 parentId
 * @param parentId 新的上级部门 ID，parentProvided 为 true 时 null 表示改为顶级部门
 */
public record DepartmentUpdateCommand(String name, boolean parentProvided, Long parentId) {

  /** 名称和上级部门都没有填写。 */
  boolean isEmpty() {
    return name == null && !parentProvided;
  }
}
