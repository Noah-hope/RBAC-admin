package com.rbac.rbac1.controller;

import static com.rbac.rbac1.controller.SessionInterceptor.ACTOR;

import com.rbac.rbac1.dto.ApiDTO;
import com.rbac.rbac1.dto.ApiUpdateDTO;
import com.rbac.rbac1.service.ApiService;
import com.rbac.rbac1.service.SessionUser;
import com.rbac.rbac1.vo.ApiVO;
import com.rbac.rbac1.vo.PageVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 接口登记：维护某个应用下的后端接口（方法、路由和接口标识），供接口级权限检查使用。 */
@RestController
@RequestMapping("/api/v1/applications/{applicationId}/apis")
@RequiredArgsConstructor
public class ApiController {

  private final ApiService apis;

  /** 分页查询应用下的接口，可按关键字筛选。 */
  @GetMapping
  public PageVO<ApiVO> list(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @PathVariable
      @Positive
      long applicationId,
      @RequestParam(defaultValue = "")
      @Size(max = 100)
      String keyword,
      @RequestParam(defaultValue = "1")
      @Positive
      int page,
      @RequestParam(defaultValue = "20")
      @Min(1)
      @Max(100)
      int pageSize) {
    return apis.list(actor, applicationId, keyword, page, pageSize);
  }

  /** 在应用下登记新接口，成功返回 201。 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiVO create(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @PathVariable
      @Positive
      long applicationId,
      @Valid
      @RequestBody
      ApiDTO input) {
    return apis.create(actor, applicationId, input);
  }

  /** 修改已登记的接口，只修改请求中出现的字段。 */
  @PatchMapping("/{apiId}")
  public ApiVO update(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @PathVariable
      @Positive
      long applicationId,
      @PathVariable
      @Positive
      long apiId,
      @Valid
      @RequestBody
      ApiUpdateDTO input) {
    return apis.update(actor, applicationId, apiId, input);
  }

  /** 删除已登记的接口，成功返回 204。 */
  @DeleteMapping("/{apiId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @PathVariable
      @Positive
      long applicationId,
      @PathVariable
      @Positive
      long apiId) {
    apis.delete(actor, applicationId, apiId);
  }
}
