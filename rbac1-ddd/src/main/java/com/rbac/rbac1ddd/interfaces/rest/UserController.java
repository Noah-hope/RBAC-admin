package com.rbac.rbac1ddd.interfaces.rest;

import static com.rbac.rbac1ddd.interfaces.rest.Ids.MAX_ID;
import static com.rbac.rbac1ddd.interfaces.rest.SessionInterceptor.OPERATOR;

import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.application.user.UserApplicationService;
import com.rbac.rbac1ddd.application.user.UserCreateCommand;
import com.rbac.rbac1ddd.application.user.UserUpdateCommand;
import com.rbac.rbac1ddd.domain.user.UserStatus;
import com.rbac.rbac1ddd.interfaces.rest.dto.UserCreateDTO;
import com.rbac.rbac1ddd.interfaces.rest.dto.UserUpdateDTO;
import com.rbac.rbac1ddd.interfaces.rest.vo.PageVO;
import com.rbac.rbac1ddd.interfaces.rest.vo.UserCreatedVO;
import com.rbac.rbac1ddd.interfaces.rest.vo.UserVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.CodePointLength;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 用户接口：用户查询、新增和修改，只做 DTO → 命令、Result → VO 的转换。 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserApplicationService users;

  /** 分页查询管理范围内的用户，可按部门（含下级部门）和关键字筛选。 */
  @GetMapping
  public PageVO<UserVO> list(
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
      @RequestParam(defaultValue = "false")
      boolean includeDepartmentName,
      @RequestParam(required = false)
      @Positive
      @Max(MAX_ID)
      Long departmentId) {
    return PageVO.from(
        users.list(operator, departmentId, keyword, includeDepartmentName, page, pageSize),
        UserVO::from);
  }

  /** 查询管理范围内的单个用户。 */
  @GetMapping("/{userId}")
  public UserVO get(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long userId) {
    return UserVO.from(users.get(operator, userId));
  }

  /** 新增用户，成功返回 201，响应禁止缓存且不含初始密码。 */
  @PostMapping
  public ResponseEntity<UserCreatedVO> create(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @Valid
      @RequestBody
      UserCreateDTO request) {
    UserCreateCommand command =
        new UserCreateCommand(
            request.getUsername(),
            request.getInitialPassword(),
            request.getName(),
            request.getDepartmentId(),
            UserStatus.valueOf(request.getStatus()),
            request.getDepartmentAdministrator());
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(UserCreatedVO.from(users.create(operator, command)));
  }

  /** 修改用户资料、账号状态、所属部门或部门管理员身份，只修改请求中出现的字段。 */
  @PatchMapping("/{userId}")
  public UserVO update(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long userId,
      @Valid
      @RequestBody
      UserUpdateDTO request) {
    UserUpdateCommand command =
        new UserUpdateCommand(
            request.getUsername(),
            request.getName(),
            request.getDepartmentId(),
            request.getStatus() == null ? null : UserStatus.valueOf(request.getStatus()),
            request.getDepartmentAdministrator());
    return UserVO.from(users.update(operator, userId, command));
  }
}
