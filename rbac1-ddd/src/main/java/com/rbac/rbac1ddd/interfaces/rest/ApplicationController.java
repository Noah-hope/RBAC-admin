package com.rbac.rbac1ddd.interfaces.rest;

import static com.rbac.rbac1ddd.interfaces.rest.Ids.MAX_ID;
import static com.rbac.rbac1ddd.interfaces.rest.SessionInterceptor.OPERATOR;

import com.rbac.rbac1ddd.application.app.BusinessApplicationService;
import com.rbac.rbac1ddd.application.app.PermissionApplicationService;
import com.rbac.rbac1ddd.application.app.PermissionCreateCommand;
import com.rbac.rbac1ddd.application.app.PermissionUpdateCommand;
import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.domain.permission.PermissionType;
import com.rbac.rbac1ddd.interfaces.rest.dto.ApplicationCreateDTO;
import com.rbac.rbac1ddd.interfaces.rest.dto.ApplicationRenameDTO;
import com.rbac.rbac1ddd.interfaces.rest.dto.PermissionCreateDTO;
import com.rbac.rbac1ddd.interfaces.rest.dto.PermissionUpdateDTO;
import com.rbac.rbac1ddd.interfaces.rest.vo.ApplicationPermissionsVO;
import com.rbac.rbac1ddd.interfaces.rest.vo.ApplicationVO;
import com.rbac.rbac1ddd.interfaces.rest.vo.PageVO;
import com.rbac.rbac1ddd.interfaces.rest.vo.PermissionVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 应用与权限接口：应用的查询、新增、改名，应用权限树和菜单、按钮权限的登记、查看、修改。
 *
 * <p>只做 DTO → 命令、Result → VO 的转换。路径参数的校验与 rbac1-structured 相同：查看单个权限时只要求 ID 为正数。
 */
@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class ApplicationController {

  private final BusinessApplicationService applications;
  private final PermissionApplicationService permissions;

  /** 按 ID 升序分页查询应用。 */
  @GetMapping
  public PageVO<ApplicationVO> list(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @RequestParam(defaultValue = "1")
      @Positive
      int page,
      @RequestParam(defaultValue = "20")
      @Positive
      @Max(100)
      int pageSize) {
    return PageVO.from(applications.list(operator, page, pageSize), ApplicationVO::from);
  }

  /** 登记应用，成功返回 201。 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApplicationVO create(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @Valid
      @RequestBody
      ApplicationCreateDTO request) {
    return ApplicationVO.from(applications.create(operator, request.name(), request.code()));
  }

  /** 修改应用名称，编码不变。 */
  @PatchMapping("/{applicationId}")
  public ApplicationVO rename(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long applicationId,
      @Valid
      @RequestBody
      ApplicationRenameDTO request) {
    return ApplicationVO.from(applications.rename(operator, applicationId, request.name()));
  }

  /** 应用的完整权限树。 */
  @GetMapping("/{applicationId}/permissions")
  public ApplicationPermissionsVO permissionTree(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long applicationId) {
    return ApplicationPermissionsVO.from(permissions.tree(operator, applicationId));
  }

  /** 登记菜单或按钮权限及其接口关联，成功返回 201。 */
  @PostMapping("/{applicationId}/permissions")
  @ResponseStatus(HttpStatus.CREATED)
  public PermissionVO createPermission(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long applicationId,
      @Valid
      @RequestBody
      PermissionCreateDTO request) {
    return PermissionVO.from(
        permissions.create(
            operator,
            applicationId,
            new PermissionCreateCommand(
                request.name(),
                request.code(),
                PermissionType.valueOf(request.type()),
                request.parentId(),
                request.apiIds() == null ? List.of() : request.apiIds())));
  }

  /** 修改权限名称、标识和接口关联，只修改请求中出现的字段。 */
  @PatchMapping("/{applicationId}/permissions/{permissionId}")
  public PermissionVO updatePermission(
      @RequestAttribute(OPERATOR)
      Operator operator,
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
    return PermissionVO.from(
        permissions.update(
            operator,
            applicationId,
            permissionId,
            new PermissionUpdateCommand(request.getName(), request.getCode(), request.getApiIds())));
  }

  /** 查看指定应用下的权限及其完整关联接口。 */
  @GetMapping("/{applicationId}/permissions/{permissionId}")
  public PermissionVO getPermission(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @PathVariable
      @Positive
      long applicationId,
      @PathVariable
      @Positive
      long permissionId) {
    return PermissionVO.from(permissions.get(operator, applicationId, permissionId));
  }
}
