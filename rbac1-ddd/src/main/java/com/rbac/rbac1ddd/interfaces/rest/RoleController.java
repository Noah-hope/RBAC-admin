package com.rbac.rbac1ddd.interfaces.rest;

import static com.rbac.rbac1ddd.interfaces.rest.Ids.MAX_ID;
import static com.rbac.rbac1ddd.interfaces.rest.SessionInterceptor.OPERATOR;

import com.rbac.rbac1ddd.application.role.RoleApplicationService;
import com.rbac.rbac1ddd.application.role.RoleCommand;
import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.domain.role.RoleType;
import com.rbac.rbac1ddd.interfaces.rest.dto.RoleDTO;
import com.rbac.rbac1ddd.interfaces.rest.vo.PageVO;
import com.rbac.rbac1ddd.interfaces.rest.vo.RoleDetailsVO;
import com.rbac.rbac1ddd.interfaces.rest.vo.RoleSummaryVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.CodePointLength;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 角色接口：只负责接收 HTTP 请求、把 DTO 转换为命令、调用应用服务、把结果转换为 VO，不包含业务规则。 */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

  private final RoleApplicationService roles;

  /** 分页查询角色，可按关键字和角色类型筛选；未传角色类型时不按类型过滤。 */
  @GetMapping
  public PageVO<RoleSummaryVO> list(
      @RequestAttribute(OPERATOR)
      Operator operator,
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
      @Pattern(regexp = "BUSINESS|TOTAL_ADMIN|DEPARTMENT_ADMIN")
      String roleType) {
    return PageVO.from(
        roles.list(
            operator, page, pageSize, keyword, roleType == null ? null : RoleType.valueOf(roleType)),
        RoleSummaryVO::from);
  }

  /** 创建角色，成功返回 201 和角色详情。 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RoleDetailsVO create(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @Valid
      @RequestBody
      RoleDTO request) {
    return RoleDetailsVO.from(roles.create(operator, toCommand(request)));
  }

  /** 查询角色详情，包括直接权限、父角色和有效权限来源。 */
  @GetMapping("/{roleId}")
  public RoleDetailsVO get(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long roleId) {
    return RoleDetailsVO.from(roles.get(operator, roleId));
  }

  /** 用请求内容整体替换角色的名称、说明、直接权限和父角色。 */
  @PutMapping("/{roleId}")
  public RoleDetailsVO update(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long roleId,
      @Valid
      @RequestBody
      RoleDTO request) {
    return RoleDetailsVO.from(roles.update(operator, roleId, toCommand(request)));
  }

  /** 删除角色，成功返回 204。 */
  @DeleteMapping("/{roleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long roleId) {
    roles.delete(operator, roleId);
  }

  /** 请求 DTO → 应用层命令；审批角色创建申请时提交的角色配置也用它转换。 */
  static RoleCommand toCommand(RoleDTO request) {
    return new RoleCommand(
        request.getName(),
        request.getDescription(),
        request.getPermissionIds(),
        request.getInheritedRoleIds());
  }
}
