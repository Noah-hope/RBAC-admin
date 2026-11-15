package com.rbac.rbac1.controller;

import static com.rbac.rbac1.controller.SessionInterceptor.ACTOR;

import com.rbac.rbac1.dto.RequestDtos.*;
import com.rbac.rbac1.dto.RoleDtos.RoleInput;
import com.rbac.rbac1.dto.SessionDtos.SessionUser;
import com.rbac.rbac1.service.RoleCreationRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/role-creation-requests")
public class RoleCreationRequestController {
  private final RoleCreationRequestService service;

  public RoleCreationRequestController(RoleCreationRequestService service) {
    this.service = service;
  }

  @GetMapping
  public PageResult<RoleCreationView> list(
      @RequestAttribute(ACTOR) SessionUser actor,
      @RequestParam(required = false) @Pattern(regexp = "PENDING|APPROVED|REJECTED") String status,
      @RequestParam(defaultValue = "1") @Positive int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
    return service.list(actor, status, page, pageSize);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RoleCreationView submit(
      @RequestAttribute(ACTOR) SessionUser actor, @Valid @RequestBody RoleInput input) {
    return service.submit(actor, input);
  }

  @GetMapping("/{requestId}")
  public RoleCreationDetails get(
      @RequestAttribute(ACTOR) SessionUser actor, @PathVariable @Positive long requestId) {
    return service.get(actor, requestId);
  }

  @PostMapping("/{requestId}/reviews")
  public RoleCreationView review(
      @RequestAttribute(ACTOR) SessionUser actor,
      @PathVariable @Positive long requestId,
      @Valid @RequestBody RoleCreationReview input) {
    return service.review(actor, requestId, input);
  }
}
