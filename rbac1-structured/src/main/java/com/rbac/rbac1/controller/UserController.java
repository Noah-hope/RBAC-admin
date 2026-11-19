package com.rbac.rbac1.controller;

import static com.rbac.rbac1.controller.SessionInterceptor.ACTOR;
import static com.rbac.rbac1.dto.Formats.MAX_ID;

import com.rbac.rbac1.dto.UserCreateDTO;
import com.rbac.rbac1.dto.UserUpdateDTO;
import com.rbac.rbac1.service.SessionUser;
import com.rbac.rbac1.service.UserService;
import com.rbac.rbac1.vo.PageVO;
import com.rbac.rbac1.vo.UserCreatedVO;
import com.rbac.rbac1.vo.UserVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.hibernate.validator.constraints.CodePointLength;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户接口：在管理范围内查询、新建和修改用户账号。 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService users;

  /** 分页查询管理范围内的用户，可按部门和关键字筛选，可选附带部门名称。 */
  @GetMapping
  public PageVO<UserVO> list(
      @RequestAttribute(ACTOR)
      SessionUser actor,
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
    return users.list(actor, departmentId, keyword, includeDepartmentName, page, pageSize);
  }

  /** 查询单个用户。 */
  @GetMapping("/{userId}")
  public UserVO get(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long userId) {
    return users.get(actor, userId);
  }

  /** 新建用户，返回 201，并禁止缓存响应。 */
  @PostMapping
  public ResponseEntity<UserCreatedVO> create(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @Valid
      @RequestBody
      UserCreateDTO request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(users.create(actor, request));
  }

  /** 修改用户，只修改请求中出现的字段。 */
  @PatchMapping("/{userId}")
  public UserVO update(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long userId,
      @Valid
      @RequestBody
      UserUpdateDTO request) {
    return users.update(actor, userId, request);
  }
}
