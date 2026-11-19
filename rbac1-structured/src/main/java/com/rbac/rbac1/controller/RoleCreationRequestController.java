package com.rbac.rbac1.controller;

import static com.rbac.rbac1.controller.SessionInterceptor.ACTOR;

import com.rbac.rbac1.dto.RoleCreationReviewDTO;
import com.rbac.rbac1.dto.RoleDTO;
import com.rbac.rbac1.service.RoleCreationRequestService;
import com.rbac.rbac1.service.SessionUser;
import com.rbac.rbac1.vo.PageVO;
import com.rbac.rbac1.vo.RoleCreationDetailsVO;
import com.rbac.rbac1.vo.RoleCreationRequestVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 角色创建申请接口：提交新角色的创建申请，并由审核人批准或拒绝。 */
@RestController
@RequestMapping("/api/v1/role-creation-requests")
@RequiredArgsConstructor
public class RoleCreationRequestController {

  private final RoleCreationRequestService service;

  /** 分页查询角色创建申请，可按状态筛选。 */
  @GetMapping
  public PageVO<RoleCreationRequestVO> list(
      @RequestAttribute(ACTOR)
      SessionUser actor,
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
    return service.list(actor, status, page, pageSize);
  }

  /** 提交角色创建申请，请求体与新增角色相同，成功返回 201。 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RoleCreationRequestVO submit(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @Valid
      @RequestBody
      RoleDTO input) {
    return service.submit(actor, input);
  }

  /** 查询角色创建申请详情。 */
  @GetMapping("/{requestId}")
  public RoleCreationDetailsVO get(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @PathVariable
      @Positive
      long requestId) {
    return service.get(actor, requestId);
  }

  /** 审核角色创建申请：批准后创建角色，拒绝则只记录结果。 */
  @PostMapping("/{requestId}/reviews")
  public RoleCreationRequestVO review(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @PathVariable
      @Positive
      long requestId,
      @Valid
      @RequestBody
      RoleCreationReviewDTO input) {
    return service.review(actor, requestId, input);
  }
}
