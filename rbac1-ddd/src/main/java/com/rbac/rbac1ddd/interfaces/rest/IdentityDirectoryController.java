package com.rbac.rbac1ddd.interfaces.rest;

import static com.rbac.rbac1ddd.interfaces.rest.Ids.MAX_ID;
import static com.rbac.rbac1ddd.interfaces.rest.SessionController.APPLICATION_HEADER;
import static com.rbac.rbac1ddd.interfaces.rest.SessionInterceptor.SESSION;

import com.rbac.rbac1ddd.application.auth.AuthenticatedSession;
import com.rbac.rbac1ddd.application.auth.IdentityDirectoryApplicationService;
import com.rbac.rbac1ddd.interfaces.rest.vo.IdentityDepartmentUserVO;
import com.rbac.rbac1ddd.interfaces.rest.vo.IdentityDepartmentVO;
import com.rbac.rbac1ddd.interfaces.rest.vo.IdentityUserVO;
import com.rbac.rbac1ddd.interfaces.rest.vo.PageVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 面向接入系统的统一身份目录，只返回部门选择和用户选择所需的字段。 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class IdentityDirectoryController {

  private final IdentityDirectoryApplicationService directory;

  /** 查询根部门或指定部门的直接下级部门。 */
  @GetMapping("/departments")
  public PageVO<IdentityDepartmentVO> departments(
      @RequestAttribute(SESSION)
      AuthenticatedSession session,
      @RequestParam(required = false)
      @Positive
      @Max(MAX_ID)
      Long parentId,
      @RequestParam(defaultValue = "")
      @Size(max = 100)
      String keyword,
      @RequestParam(defaultValue = "1")
      @Positive
      int page,
      @RequestParam(defaultValue = "20")
      @Positive
      @Max(100)
      int pageSize,
      @RequestHeader(value = APPLICATION_HEADER, required = false)
      String application) {
    return PageVO.from(
        directory.departments(session, application, parentId, keyword, page, pageSize),
        IdentityDepartmentVO::from);
  }

  /** 分页查询指定部门下的启用账号，不递归到该部门的下级部门。 */
  @GetMapping("/departments/{departmentId}/users")
  public PageVO<IdentityDepartmentUserVO> departmentUsers(
      @RequestAttribute(SESSION)
      AuthenticatedSession session,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long departmentId,
      @RequestParam(defaultValue = "")
      @Size(max = 100)
      String keyword,
      @RequestParam(defaultValue = "1")
      @Positive
      int page,
      @RequestParam(defaultValue = "20")
      @Positive
      @Max(100)
      int pageSize,
      @RequestHeader(value = APPLICATION_HEADER, required = false)
      String application) {
    return PageVO.from(
        directory.departmentUsers(session, application, departmentId, keyword, page, pageSize),
        IdentityDepartmentUserVO::from);
  }

  /** 按统一身份 ID 查询单个组织用户。 */
  @GetMapping("/users/{userId}")
  public IdentityUserVO user(
      @RequestAttribute(SESSION)
      AuthenticatedSession session,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long userId,
      @RequestHeader(value = APPLICATION_HEADER, required = false)
      String application) {
    return IdentityUserVO.from(directory.user(session, application, userId));
  }
}
