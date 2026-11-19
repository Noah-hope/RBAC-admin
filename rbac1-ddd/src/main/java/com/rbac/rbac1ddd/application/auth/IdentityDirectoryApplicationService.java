package com.rbac.rbac1ddd.application.auth;

import static com.rbac.rbac1ddd.domain.shared.DomainException.Reason.NOT_FOUND;

import com.rbac.rbac1ddd.application.auth.IdentityDirectoryReader.DepartmentResult;
import com.rbac.rbac1ddd.application.auth.IdentityDirectoryReader.DepartmentUserResult;
import com.rbac.rbac1ddd.application.auth.IdentityDirectoryReader.UserResult;
import com.rbac.rbac1ddd.application.shared.PageResult;
import com.rbac.rbac1ddd.domain.department.Department;
import com.rbac.rbac1ddd.domain.department.DepartmentRepository;
import com.rbac.rbac1ddd.domain.shared.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 组织统一身份目录查询，先检查目标应用授权，再读取目录。 */
@Service
@RequiredArgsConstructor
public class IdentityDirectoryApplicationService {

  private final AccessApplicationService access;
  private final DepartmentRepository departments;
  private final IdentityDirectoryReader directory;

  /** 分页查询根部门或指定部门的直接下级。 */
  @Transactional(readOnly = true)
  public PageResult<DepartmentResult> departments(
      AuthenticatedSession session, String application, Long parentId, String keyword,
      int page, int pageSize) {
    access.requireIdentityDirectoryApi(session, application, "listIdentityDepartments");
    if (parentId != null) {
      requireDepartment(parentId);
    }
    return directory.departments(parentId, keyword.strip(), page, pageSize);
  }

  /** 查找指定部门的启用用户，搜索范围仅限该部门。 */
  @Transactional(readOnly = true)
  public PageResult<DepartmentUserResult> departmentUsers(
      AuthenticatedSession session, String application, long departmentId, String keyword,
      int page, int pageSize) {
    access.requireIdentityDirectoryApi(session, application, "listIdentityDepartmentUsers");
    Department department = requireDepartment(departmentId);
    return directory.departmentUsers(departmentId, department.getName(), keyword.strip(), page, pageSize);
  }

  /** 按统一身份 ID 读取一个仍可用于选人的组织账号。 */
  @Transactional(readOnly = true)
  public UserResult user(AuthenticatedSession session, String application, long userId) {
    access.requireIdentityDirectoryApi(session, application, "lookupIdentityUser");
    return directory.user(userId).orElseThrow(() -> new DomainException(NOT_FOUND, "用户不存在。"));
  }

  /** 选中已不存在的部门时返回明确的业务错误。 */
  private Department requireDepartment(long departmentId) {
    return departments.findById(departmentId)
        .orElseThrow(() -> new DomainException(NOT_FOUND, "部门不存在。"));
  }
}
