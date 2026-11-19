package com.rbac.rbac1.service;

import static com.rbac.rbac1.service.ManagementFailure.Reason.NOT_FOUND;

import com.rbac.rbac1.entity.Department;
import com.rbac.rbac1.entity.UserAccount;
import com.rbac.rbac1.mapper.DepartmentMapper;
import com.rbac.rbac1.mapper.UserMapper;
import com.rbac.rbac1.vo.IdentityDepartmentUserVO;
import com.rbac.rbac1.vo.IdentityDepartmentVO;
import com.rbac.rbac1.vo.IdentityUserVO;
import com.rbac.rbac1.vo.PageVO;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 统一身份目录查询。按接入应用的 API 授权读取部门和启用的组织账号。 */
@Service
@RequiredArgsConstructor
public class IdentityDirectoryService {

  private final PermissionCheckService access;
  private final DepartmentMapper departments;
  private final UserMapper users;

  /** 分页查询根部门或指定部门的直接下级。 */
  @Transactional(readOnly = true)
  public PageVO<IdentityDepartmentVO> departments(
      long actorId, String application, Long parentId, String keyword, int page, int pageSize) {
    access.requireIdentityDirectoryApi(actorId, application, "listIdentityDepartments");
    if (parentId != null) {
      requireDepartment(parentId);
    }

    Page<Department> result =
        departments.findDirectoryLevel(parentId, keyword.strip(), PageRequest.of(page - 1, pageSize));
    List<Long> ids = result.getContent().stream().map(Department::getId).toList();
    Set<Long> parents =
        ids.isEmpty() ? Set.of() : Set.copyOf(departments.findDirectoryParentIds(ids));
    List<IdentityDepartmentVO> items =
        result.getContent().stream()
            .map(department -> new IdentityDepartmentVO(
                department.getId(), department.getId().toString(), department.getName(),
                department.getParentId(), parents.contains(department.getId())))
            .toList();
    return new PageVO<>(items, page, pageSize, result.getTotalElements());
  }

  /** 查找指定部门的启用用户，搜索范围仅限该部门。 */
  @Transactional(readOnly = true)
  public PageVO<IdentityDepartmentUserVO> departmentUsers(
      long actorId, String application, long departmentId, String keyword, int page, int pageSize) {
    access.requireIdentityDirectoryApi(actorId, application, "listIdentityDepartmentUsers");
    Department department = requireDepartment(departmentId);
    Page<UserAccount> result = users.findDirectoryUsers(
        departmentId, UserAccount.ACTIVE, keyword.strip(), PageRequest.of(page - 1, pageSize));
    List<IdentityDepartmentUserVO> items = result.getContent().stream()
        .map(user -> new IdentityDepartmentUserVO(user.getId().toString(), user.getUsername(),
            user.getName(), departmentId, department.getName()))
        .toList();
    return new PageVO<>(items, page, pageSize, result.getTotalElements());
  }

  /** 按统一身份 ID 读取一个仍可用于选人的组织账号。 */
  @Transactional(readOnly = true)
  public IdentityUserVO user(long actorId, String application, long userId) {
    access.requireIdentityDirectoryApi(actorId, application, "lookupIdentityUser");
    return users.findById(userId)
        .filter(user -> user.getDepartmentId() != null && UserAccount.ACTIVE.equals(user.getStatus()))
        .map(user -> new IdentityUserVO(user.getId().toString(), user.getUsername(), user.getName()))
        .orElseThrow(() -> new ManagementFailure(NOT_FOUND, "用户不存在。"));
  }

  /** 选中已不存在的部门时返回明确的业务错误。 */
  private Department requireDepartment(long departmentId) {
    return departments.findById(departmentId)
        .orElseThrow(() -> new ManagementFailure(NOT_FOUND, "部门不存在。"));
  }
}
