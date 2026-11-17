package com.rbac.rbac1ddd.interfaces.rest;

import static com.rbac.rbac1ddd.interfaces.rest.Ids.MAX_ID;
import static com.rbac.rbac1ddd.interfaces.rest.SessionInterceptor.OPERATOR;

import com.rbac.rbac1ddd.application.user.BatchAction;
import com.rbac.rbac1ddd.application.user.UserRoleApplicationService;
import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.interfaces.rest.dto.BatchRoleChangeDTO;
import com.rbac.rbac1ddd.interfaces.rest.dto.ReplaceUserRolesDTO;
import com.rbac.rbac1ddd.interfaces.rest.vo.BatchRoleVO;
import com.rbac.rbac1ddd.interfaces.rest.vo.UserRoleVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 用户角色关联接口：整体替换单个用户的角色，或对多个用户批量添加、移除角色。 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserRoleController {

  private final UserRoleApplicationService users;

  /** 用请求中的角色列表整体替换指定用户的角色，返回替换后的用户角色。 */
  @PutMapping("/users/{userId}/roles")
  public UserRoleVO replace(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long userId,
      @Valid
      @RequestBody
      ReplaceUserRolesDTO request) {
    return UserRoleVO.from(users.replace(operator, userId, request.roleIds()));
  }

  /** 按请求中的动作对多个用户批量添加或移除角色。 */
  @PatchMapping("/user-role-assignments")
  public BatchRoleVO batch(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @Valid
      @RequestBody
      BatchRoleChangeDTO request) {
    return BatchRoleVO.from(
        users.batch(
            operator, BatchAction.valueOf(request.action()), request.userIds(), request.roleIds()));
  }
}
