package com.rbac.rbac1.controller;

import static com.rbac.rbac1.controller.SessionInterceptor.ACTOR;
import static com.rbac.rbac1.dto.DepartmentDtos.MAX_ID;

import com.rbac.rbac1.dto.AuthorizationDtos.BatchRoleChange;
import com.rbac.rbac1.dto.AuthorizationDtos.BatchRoleResult;
import com.rbac.rbac1.dto.AuthorizationDtos.MyPermissions;
import com.rbac.rbac1.dto.AuthorizationDtos.ReplaceUserRoles;
import com.rbac.rbac1.dto.AuthorizationDtos.UserRoleResult;
import com.rbac.rbac1.dto.SessionDtos.SessionUser;
import com.rbac.rbac1.service.PermissionCheckService;
import com.rbac.rbac1.service.UserAuthorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthorizationController {

  private final UserAuthorizationService assignments;
  private final PermissionCheckService permissions;

  public AuthorizationController(
      UserAuthorizationService assignments, PermissionCheckService permissions) {
    this.assignments = assignments;
    this.permissions = permissions;
  }

  @PutMapping("/users/{userId}/roles")
  public UserRoleResult replace(
      @RequestAttribute(ACTOR) SessionUser actor,
      @PathVariable @Positive @Max(MAX_ID) long userId,
      @Valid @RequestBody ReplaceUserRoles request) {
    return assignments.replace(actor, userId, request.roleIds());
  }

  @PatchMapping("/user-role-assignments")
  public BatchRoleResult batch(
      @RequestAttribute(ACTOR) SessionUser actor, @Valid @RequestBody BatchRoleChange request) {
    return assignments.batch(actor, request);
  }

  @GetMapping("/me/permissions")
  public MyPermissions mine(@RequestAttribute(ACTOR) SessionUser actor) {
    return permissions.mine(actor.id());
  }
}
