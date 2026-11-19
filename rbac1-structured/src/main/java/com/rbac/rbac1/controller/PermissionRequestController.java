package com.rbac.rbac1.controller;

import static com.rbac.rbac1.controller.SessionInterceptor.ACTOR;

import com.rbac.rbac1.dto.PermissionRequestDTO;
import com.rbac.rbac1.dto.PermissionReviewDTO;
import com.rbac.rbac1.service.PermissionRequestService;
import com.rbac.rbac1.service.SessionUser;
import com.rbac.rbac1.vo.PageVO;
import com.rbac.rbac1.vo.PermissionRequestVO;
import com.rbac.rbac1.vo.RequestablePermissionsVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 权限申请接口：用户申请权限、撤回申请，管理员审核申请。 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PermissionRequestController {

  private final PermissionRequestService service;

  /** 返回当前用户可以申请的权限。 */
  @GetMapping("/me/requestable-permissions")
  public RequestablePermissionsVO requestable(
      @RequestAttribute(ACTOR)
      SessionUser actor) {
    return service.requestable(actor);
  }

  /** 分页查询权限申请：MINE 为自己提交的申请，REVIEW 为待自己审核的申请，可按状态筛选。 */
  @GetMapping("/permission-requests")
  public PageVO<PermissionRequestVO> list(
      @RequestAttribute(ACTOR)
      SessionUser actor,
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
    return service.list(actor, view, status, page, pageSize);
  }

  /** 提交权限申请，成功返回 201。 */
  @PostMapping("/permission-requests")
  @ResponseStatus(HttpStatus.CREATED)
  public PermissionRequestVO submit(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @Valid
      @RequestBody
      PermissionRequestDTO input) {
    return service.submit(actor, input);
  }

  /** 查询单个权限申请。 */
  @GetMapping("/permission-requests/{requestId}")
  public PermissionRequestVO get(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @PathVariable
      @Positive
      long requestId) {
    return service.get(actor, requestId);
  }

  /** 撤回自己提交的申请。 */
  @PostMapping("/permission-requests/{requestId}/withdrawals")
  public PermissionRequestVO withdraw(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @PathVariable
      @Positive
      long requestId) {
    return service.withdraw(actor, requestId);
  }

  /** 审核权限申请：批准或拒绝。 */
  @PostMapping("/permission-requests/{requestId}/reviews")
  public PermissionRequestVO review(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @PathVariable
      @Positive
      long requestId,
      @Valid
      @RequestBody
      PermissionReviewDTO input) {
    return service.review(actor, requestId, input);
  }
}
