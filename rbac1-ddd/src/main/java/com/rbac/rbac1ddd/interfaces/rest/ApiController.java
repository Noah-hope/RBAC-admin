package com.rbac.rbac1ddd.interfaces.rest;

import static com.rbac.rbac1ddd.interfaces.rest.SessionInterceptor.OPERATOR;

import com.rbac.rbac1ddd.application.app.ApiApplicationService;
import com.rbac.rbac1ddd.application.app.ApiCommand;
import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.interfaces.rest.dto.ApiDTO;
import com.rbac.rbac1ddd.interfaces.rest.dto.ApiUpdateDTO;
import com.rbac.rbac1ddd.interfaces.rest.vo.ApiVO;
import com.rbac.rbac1ddd.interfaces.rest.vo.PageVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 应用 API 清单接口：查询、登记、编辑和移除接口登记。
 *
 * <p>只做 DTO → 命令、Result → VO 的转换。路径参数的校验与 rbac1-structured 相同：只要求 ID 为正数。
 */
@RestController
@RequestMapping("/api/v1/applications/{applicationId}/apis")
@RequiredArgsConstructor
public class ApiController {

  private final ApiApplicationService apis;

  /** 按关键字（匹配 operationId 或路由）分页查询应用下的接口。 */
  @GetMapping
  public PageVO<ApiVO> list(
      @RequestAttribute(OPERATOR)
      Operator operator,
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
    return PageVO.from(apis.list(operator, applicationId, keyword, page, pageSize), ApiVO::from);
  }

  /** 登记接口，成功返回 201。 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ApiVO create(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @PathVariable
      @Positive
      long applicationId,
      @Valid
      @RequestBody
      ApiDTO request) {
    return ApiVO.from(
        apis.create(
            operator,
            applicationId,
            new ApiCommand(request.operationId(), request.httpMethod(), request.path())));
  }

  /** 修改接口资料，只修改请求中出现的字段。 */
  @PatchMapping("/{apiId}")
  public ApiVO update(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @PathVariable
      @Positive
      long applicationId,
      @PathVariable
      @Positive
      long apiId,
      @Valid
      @RequestBody
      ApiUpdateDTO request) {
    return ApiVO.from(
        apis.update(
            operator,
            applicationId,
            apiId,
            new ApiCommand(request.getOperationId(), request.getHttpMethod(), request.getPath())));
  }

  /** 移除接口登记及其全部权限关联，成功返回 204。 */
  @DeleteMapping("/{apiId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @PathVariable
      @Positive
      long applicationId,
      @PathVariable
      @Positive
      long apiId) {
    apis.delete(operator, applicationId, apiId);
  }
}
