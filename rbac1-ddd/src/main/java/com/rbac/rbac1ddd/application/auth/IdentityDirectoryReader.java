package com.rbac.rbac1ddd.application.auth;

import com.rbac.rbac1ddd.application.shared.PageResult;
import java.util.Optional;

/** 统一身份目录的只读端口，直接查询选择器所需的数据。 */
public interface IdentityDirectoryReader {

  /** 部门目录项，包含浏览器判断能否继续展开所需的信息。 */
  record DepartmentResult(long id, String name, Long parentId, boolean hasChildren) {}

  /** 部门中的启用用户及其所属部门。 */
  record DepartmentUserResult(long userId, String no, String name, long departmentId, String departmentName) {}

  /** 统一身份用户的姓名和账号。 */
  record UserResult(long userId, String no, String name) {}

  /** 分页读取根部门或已存在部门的直接子部门。 */
  PageResult<DepartmentResult> departments(Long parentId, String keyword, int page, int pageSize);

  /** 在已存在部门中按账号或姓名分页查询启用用户。 */
  PageResult<DepartmentUserResult> departmentUsers(
      long departmentId, String departmentName, String keyword, int page, int pageSize);

  /** 按统一身份 ID 查找组织内的启用账号。 */
  Optional<UserResult> user(long userId);
}
