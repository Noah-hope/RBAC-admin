package com.rbac.rbac1.controller;

import static com.rbac.rbac1.controller.SessionInterceptor.ACTOR;
import static com.rbac.rbac1.dto.Formats.MAX_ID;

import com.rbac.rbac1.dto.RoleDTO;
import com.rbac.rbac1.service.RoleService;
import com.rbac.rbac1.service.SessionUser;
import com.rbac.rbac1.vo.PageVO;
import com.rbac.rbac1.vo.RoleDetailsVO;
import com.rbac.rbac1.vo.RoleSummaryVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.CodePointLength;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 角色接口：角色的查询、新增、整体修改和删除，包括角色的上级继承关系和权限。 */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

  private final RoleService roles;

  /** 分页查询角色，可按关键字和角色类型筛选。 */
  @GetMapping
  public PageVO<RoleSummaryVO> list(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @RequestParam(defaultValue = "1")
      @Positive
      int page,
      @RequestParam(defaultValue = "20")
      @Positive
      @Max(100)
      int pageSize,
      @RequestParam(defaultValue = "")
      @CodePointLength(max = 100)
      String keyword,
      @RequestParam(required = false)
      @jakarta.validation.constraints.Pattern(regexp = "BUSINESS|TOTAL_ADMIN|DEPARTMENT_ADMIN")
      String roleType) {
    return roles.list(actor, page, pageSize, keyword, roleType);
  }

  /** 新增角色，成功返回 201。 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RoleDetailsVO create(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @Valid
      @RequestBody
      RoleDTO request) {
    return roles.create(actor, request);
  }

  /** 查询角色详情。 */
  @GetMapping("/{roleId}")
  public RoleDetailsVO get(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long roleId) {
    return roles.get(actor, roleId);
  }

  /** 用请求内容整体替换角色的名称、描述、继承的角色和权限。 */
  @PutMapping("/{roleId}")
  public RoleDetailsVO update(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long roleId,
      @Valid
      @RequestBody
      RoleDTO request) {
    return roles.update(actor, roleId, request);
  }

  /** 删除角色，成功返回 204。 */
  @DeleteMapping("/{roleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long roleId) {
    roles.delete(actor, roleId);
  }
}
