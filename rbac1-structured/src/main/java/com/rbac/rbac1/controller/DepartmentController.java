package com.rbac.rbac1.controller;

import static com.rbac.rbac1.controller.SessionInterceptor.ACTOR;
import static com.rbac.rbac1.dto.Formats.MAX_ID;

import com.rbac.rbac1.dto.DepartmentDTO;
import com.rbac.rbac1.dto.DepartmentUpdateDTO;
import com.rbac.rbac1.service.DepartmentService;
import com.rbac.rbac1.service.SessionUser;
import com.rbac.rbac1.vo.DepartmentTreeVO;
import com.rbac.rbac1.vo.DepartmentVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 部门接口：部门树查询、新增和修改部门。 */
@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

  private final DepartmentService departments;

  /** 按操作者的管理范围返回部门树。 */
  @GetMapping
  public DepartmentTreeVO list(
      @RequestAttribute(ACTOR)
      SessionUser actor) {
    return departments.listTree(actor);
  }

  /** 新增部门，成功返回 201。 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public DepartmentVO create(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @Valid
      @RequestBody
      DepartmentDTO request) {
    return departments.create(actor, request);
  }

  /** 修改部门名称或上级部门，只修改请求中出现的字段。 */
  @PatchMapping("/{departmentId}")
  public DepartmentVO update(
      @RequestAttribute(ACTOR)
      SessionUser actor,
      @PathVariable
      @Positive
      @Max(MAX_ID)
      long departmentId,
      @Valid
      @RequestBody
      DepartmentUpdateDTO request) {
    return departments.update(actor, departmentId, request);
  }
}
