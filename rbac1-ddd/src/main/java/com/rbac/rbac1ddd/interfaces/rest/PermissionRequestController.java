package com.rbac.rbac1ddd.interfaces.rest;

import static com.rbac.rbac1ddd.interfaces.rest.SessionInterceptor.OPERATOR;

import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.application.request.PermissionRequestApplicationService;
import com.rbac.rbac1ddd.domain.request.RequestStatus;
import com.rbac.rbac1ddd.domain.request.ReviewDecision;
import com.rbac.rbac1ddd.interfaces.rest.dto.PermissionRequestDTO;
import com.rbac.rbac1ddd.interfaces.rest.dto.PermissionReviewDTO;
import com.rbac.rbac1ddd.interfaces.rest.vo.PageVO;
import com.rbac.rbac1ddd.interfaces.rest.vo.PermissionRequestVO;
import com.rbac.rbac1ddd.interfaces.rest.vo.RequestablePermissionsVO;
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
 * 权限申请接口：申请目录、提交、查询、撤回和审核。
 *
 * <p>只做 DTO → 参数、Result → VO 的转换。路径参数的校验与 rbac1-structured 相同：只要求 ID 为正数。
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PermissionRequestController {

  /** 审核视图的查询参数取值。 */
  private static final String REVIEW_VIEW = "REVIEW";

  private final PermissionRequestApplicationService requests;

  /** 本人可申请的权限目录。 */
  @GetMapping("/me/requestable-permissions")
  public RequestablePermissionsVO requestable(
      @RequestAttribute(OPERATOR)
      Operator operator) {
    return RequestablePermissionsVO.from(requests.requestable(operator));
  }

  /** 分页查询申请：view=MINE 看本人提交的，view=REVIEW 看审核范围内的。 */
  @GetMapping("/permission-requests")
  public PageVO<PermissionRequestVO> list(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @RequestParam(defaultValue = "MINE")
      @Pattern(regexp = "MINE|REVIEW")
      String view,
      @RequestParam(required = false)
      @Pattern(regexp = "PENDING|APPROVED|REJECTED|SATISFIED|WITHDRAWN")
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
            operator,
            REVIEW_VIEW.equals(view),
            status == null ? null : RequestStatus.valueOf(status),
            page,
            pageSize),
        PermissionRequestVO::from);
  }

  /** 提交权限申请，成功返回 201。 */
  @PostMapping("/permission-requests")
  @ResponseStatus(HttpStatus.CREATED)
  public PermissionRequestVO submit(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @Valid
      @RequestBody
      PermissionRequestDTO request) {
    return PermissionRequestVO.from(
        requests.submit(operator, request.permissionId(), request.reason()));
  }

  /** 查看申请详情，有权审核者同时看到审批候选角色。 */
  @GetMapping("/permission-requests/{requestId}")
  public PermissionRequestVO get(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @PathVariable
      @Positive
      long requestId) {
    return PermissionRequestVO.from(requests.get(operator, requestId));
  }

  /** 申请人撤回待审核申请。 */
  @PostMapping("/permission-requests/{requestId}/withdrawals")
  public PermissionRequestVO withdraw(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @PathVariable
      @Positive
      long requestId) {
    return PermissionRequestVO.from(requests.withdraw(operator, requestId));
  }

  /** 审核权限申请：通过时选择业务角色，拒绝时不提交角色。 */
  @PostMapping("/permission-requests/{requestId}/reviews")
  public PermissionRequestVO review(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @PathVariable
      @Positive
      long requestId,
      @Valid
      @RequestBody
      PermissionReviewDTO request) {
    return PermissionRequestVO.from(
        requests.review(
            operator, requestId, ReviewDecision.valueOf(request.decision()), request.roleId()));
  }
}
