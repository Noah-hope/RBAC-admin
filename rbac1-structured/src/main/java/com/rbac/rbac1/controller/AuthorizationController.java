package com.rbac.rbac1.controller;

import static com.rbac.rbac1.controller.SessionInterceptor.ACTOR;
import static com.rbac.rbac1.dto.Formats.MAX_ID;

import com.rbac.rbac1.dto.BatchRoleChangeDTO;
import com.rbac.rbac1.dto.ReplaceUserRolesDTO;
import com.rbac.rbac1.service.PermissionCheckService;
import com.rbac.rbac1.service.SessionUser;
import com.rbac.rbac1.service.UserAuthorizationService;
import com.rbac.rbac1.vo.BatchRoleVO;
import com.rbac.rbac1.vo.MyPermissionsVO;
import com.rbac.rbac1.vo.UserRoleVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户授权接口：替换单个用户的角色、批量增减角色，以及查询当前用户自己的权限。 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthorizationController {

  private final UserAuthorizationService assignments;
  private final PermissionCheckService permissions;

  /** 用请求中的角色列表整体替换用户的角色。 */
  @PutMapping("/users/{userId}/roles")
  public UserRoleVO replace(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long userId,
      @Valid
      @RequestBody
      ReplaceUserRolesDTO request) {
    return assignments.replace(actor, userId, request.roleIds());
  }

  /** 给多个用户批量添加或移除角色。 */
  @PatchMapping("/user-role-assignments")
  public BatchRoleVO batch(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @Valid
      @RequestBody
      BatchRoleChangeDTO request) {
    return assignments.batch(actor, request);
  }

  /** 返回当前用户的角色和权限，包括通过角色继承获得的权限。 */
  @GetMapping("/me/permissions")
  public MyPermissionsVO mine(
      @RequestAttribute(ACTOR)
      SessionUser actor) {
    return permissions.mine(actor.id());
  }
}
