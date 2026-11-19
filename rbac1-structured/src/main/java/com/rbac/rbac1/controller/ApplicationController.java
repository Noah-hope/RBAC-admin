package com.rbac.rbac1.controller;

import static com.rbac.rbac1.controller.SessionInterceptor.ACTOR;
import static com.rbac.rbac1.dto.Formats.MAX_ID;

import com.rbac.rbac1.dto.ApplicationCreateDTO;
import com.rbac.rbac1.dto.ApplicationRenameDTO;
import com.rbac.rbac1.dto.PermissionCreateDTO;
import com.rbac.rbac1.dto.PermissionUpdateDTO;
import com.rbac.rbac1.service.ApplicationService;
import com.rbac.rbac1.service.SessionUser;
import com.rbac.rbac1.vo.ApplicationPermissionsVO;
import com.rbac.rbac1.vo.ApplicationVO;
import com.rbac.rbac1.vo.PageVO;
import com.rbac.rbac1.vo.PermissionVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 应用与权限点接口：维护接入的业务应用，以及每个应用下的权限树。 */
@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class ApplicationController {

  private final ApplicationService applications;

  /** 分页查询应用。 */
  @GetMapping
  public PageVO<ApplicationVO> list(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @RequestParam(defaultValue = "1")
      @Positive
      int page,
      @RequestParam(defaultValue = "20")
      @Positive
      @Max(100)
      int pageSize) {
    return applications.list(actor, page, pageSize);
  }

  /** 接入新应用，成功返回 201。 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApplicationVO create(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @Valid
      @RequestBody
      ApplicationCreateDTO request) {
    return applications.create(actor, request);
  }

  /** 修改应用名称；应用编码创建后不可修改。 */
  @PatchMapping("/{applicationId}")
  public ApplicationVO rename(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long applicationId,
      @Valid
      @RequestBody
      ApplicationRenameDTO request) {
    return applications.rename(actor, applicationId, request);
  }

  /** 返回应用下完整的权限树，一次返回不分页。 */
  @GetMapping("/{applicationId}/permissions")
  public ApplicationPermissionsVO permissionTree(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long applicationId) {
    return applications.permissionTree(actor, applicationId);
  }

  /** 在应用下新增权限点，成功返回 201。 */
  @PostMapping("/{applicationId}/permissions")
  @ResponseStatus(HttpStatus.CREATED)
  public PermissionVO createPermission(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long applicationId,
      @Valid
      @RequestBody
      PermissionCreateDTO request) {
    return applications.createPermission(actor, applicationId, request);
  }

  /** 修改权限点，只修改请求中出现的字段。 */
  @PatchMapping("/{applicationId}/permissions/{permissionId}")
  public PermissionVO updatePermission(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long applicationId,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long permissionId,
      @Valid
      @RequestBody
      PermissionUpdateDTO request) {
    return applications.updatePermission(actor, applicationId, permissionId, request);
  }

  /** 查询单个权限点；权限点必须属于路径中的应用。 */
  @GetMapping("/{applicationId}/permissions/{permissionId}")
  public PermissionVO getPermission(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @PathVariable
      @Positive
      long applicationId,
      @PathVariable
      @Positive
      long permissionId) {
    return applications.getPermission(actor, applicationId, permissionId);
  }
}
