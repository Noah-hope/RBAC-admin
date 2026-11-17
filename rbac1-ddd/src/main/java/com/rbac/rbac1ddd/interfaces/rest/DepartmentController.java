package com.rbac.rbac1ddd.interfaces.rest;

import static com.rbac.rbac1ddd.interfaces.rest.Ids.MAX_ID;
import static com.rbac.rbac1ddd.interfaces.rest.SessionInterceptor.OPERATOR;

import com.rbac.rbac1ddd.application.auth.Operator;
import com.rbac.rbac1ddd.application.department.DepartmentApplicationService;
import com.rbac.rbac1ddd.application.department.DepartmentUpdateCommand;
import com.rbac.rbac1ddd.interfaces.rest.dto.DepartmentDTO;
import com.rbac.rbac1ddd.interfaces.rest.dto.DepartmentUpdateDTO;
import com.rbac.rbac1ddd.interfaces.rest.vo.DepartmentTreeVO;
import com.rbac.rbac1ddd.interfaces.rest.vo.DepartmentVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 部门接口：部门树查询、新增和修改部门，只做 DTO → 命令、Result → VO 的转换。 */
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

  private final DepartmentApplicationService departments;

  /** 按操作者的管理范围返回部门树。 */
  @GetMapping
  public DepartmentTreeVO tree(
      @RequestAttribute(OPERATOR)
      Operator operator) {
    return DepartmentTreeVO.from(departments.tree(operator));
  }

  /** 新增部门，成功返回 201。 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public DepartmentVO create(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @Valid
      @RequestBody
      DepartmentDTO request) {
    return DepartmentVO.from(departments.create(operator, request.name(), request.parentId()));
  }

  /** 修改部门名称或上级部门，只修改请求中出现的字段。 */
  @PatchMapping("/{departmentId}")
  public DepartmentVO update(
      @RequestAttribute(OPERATOR)
      Operator operator,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long departmentId,
      @Valid
      @RequestBody
      DepartmentUpdateDTO request) {
    return DepartmentVO.from(
        departments.update(
            operator,
            departmentId,
            new DepartmentUpdateCommand(
                request.getName(), request.isParentProvided(), request.getParentId())));
  }
}
