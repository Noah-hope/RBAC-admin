package com.rbac.rbac1ddd.interfaces.rest;

import static com.rbac.rbac1ddd.interfaces.rest.SessionInterceptor.OPERATOR;

import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.application.request.RoleCreationRequestApplicationService;
import com.rbac.rbac1ddd.domain.request.RequestStatus;
import com.rbac.rbac1ddd.domain.request.ReviewDecision;
import com.rbac.rbac1ddd.interfaces.rest.dto.RoleCreationReviewDTO;
import com.rbac.rbac1ddd.interfaces.rest.dto.RoleDTO;
import com.rbac.rbac1ddd.interfaces.rest.vo.PageVO;
import com.rbac.rbac1ddd.interfaces.rest.vo.RoleCreationDetailsVO;
import com.rbac.rbac1ddd.interfaces.rest.vo.RoleCreationRequestVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色创建申请接口：提交、查询和审核。
 *
 * <p>只做 DTO → 命令、Result → VO 的转换。申请表单与新建角色相同，使用 {@link RoleDTO}。
 */
@RestController
@RequestMapping("/api/v1/role-creation-requests")
@RequiredArgsConstructor
public class RoleCreationRequestController {

  private final RoleCreationRequestApplicationService requests;

  /** 分页查询申请：部门管理员只看本人提交的，总管理员看全部。 */
  @GetMapping
  public PageVO<RoleCreationRequestVO> list(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @RequestParam(required = false)
      @Pattern(regexp = "PENDING|APPROVED|REJECTED")
      String status,
      @RequestParam(defaultValue = "1")
      @Positive
      int page,
      @RequestParam(defaultValue = "20")
      @Min(1)
      @Max(100)
      int pageSize) {
    return PageVO.from(
        requests.list(
            operator, status == null ? null : RequestStatus.valueOf(status), page, pageSize),
        RoleCreationRequestVO::from);
  }

  /** 部门管理员提交角色创建申请，成功返回 201。 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RoleCreationRequestVO submit(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @Valid
      @RequestBody
      RoleDTO request) {
    return RoleCreationRequestVO.from(
        requests.submit(operator, RoleController.toCommand(request)));
  }

  /** 查看申请详情和按父角色当前配置计算的有效权限预览。 */
  @GetMapping("/{requestId}")
  public RoleCreationDetailsVO get(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @PathVariable
      @Positive
      long requestId) {
    return RoleCreationDetailsVO.from(requests.get(operator, requestId));
  }

  /** 总管理员审核申请：通过时提交最终角色配置并实际创建角色。 */
  @PostMapping("/{requestId}/reviews")
  public RoleCreationRequestVO review(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @PathVariable
      @Positive
      long requestId,
      @Valid
      @RequestBody
      RoleCreationReviewDTO request) {
    return RoleCreationRequestVO.from(
        requests.review(
            operator,
            requestId,
            ReviewDecision.valueOf(request.decision()),
            request.role() == null ? null : RoleController.toCommand(request.role())));
  }
}
