package com.rbac.rbac1.controller;

import static com.rbac.rbac1.controller.SessionController.APPLICATION_HEADER;
import static com.rbac.rbac1.controller.SessionInterceptor.ACTOR;
import static com.rbac.rbac1.controller.SessionInterceptor.AUDIENCE;

import static com.rbac.rbac1.dto.Formats.MAX_ID;
import com.rbac.rbac1.service.IdentityDirectoryService;
import com.rbac.rbac1.service.SessionService;
import com.rbac.rbac1.service.SessionUser;
import com.rbac.rbac1.vo.IdentityDepartmentUserVO;
import com.rbac.rbac1.vo.IdentityDepartmentVO;
import com.rbac.rbac1.vo.IdentityUserVO;
import com.rbac.rbac1.vo.PageVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
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

  private final IdentityDirectoryService directory;
  private final SessionService sessions;

  /** 查询根部门或指定部门的直接下级部门。 */
  @GetMapping("/departments")
  public PageVO<IdentityDepartmentVO> departments(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @RequestAttribute(AUDIENCE)
      List<String> audience,
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
    return directory.departments(
        actor.id(),
        sessions.applicationContext(audience, application),
        parentId, keyword, page, pageSize);
  }

  /** 分页查询指定部门下的启用账号，不递归到该部门的下级部门。 */
  @GetMapping("/departments/{departmentId}/users")
  public PageVO<IdentityDepartmentUserVO> departmentUsers(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @RequestAttribute(AUDIENCE)
      List<String> audience,
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
    return directory.departmentUsers(
        actor.id(),
        sessions.applicationContext(audience, application),
        departmentId, keyword, page, pageSize);
  }

  /** 按统一身份 ID 查询单个组织用户。 */
  @GetMapping("/users/{userId}")
  public IdentityUserVO user(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @RequestAttribute(AUDIENCE)
      List<String> audience,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long userId,
      @RequestHeader(value = APPLICATION_HEADER, required = false)
      String application) {
    return directory.user(actor.id(), sessions.applicationContext(audience, application), userId);
  }
}
