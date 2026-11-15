package com.rbac.rbac0.controller;

import com.rbac.rbac0.dto.DepartmentDtos.DepartmentInput;
import com.rbac.rbac0.dto.DepartmentDtos.DepartmentTree;
import com.rbac.rbac0.dto.DepartmentDtos.DepartmentUpdate;
import com.rbac.rbac0.dto.DepartmentDtos.DepartmentView;
import com.rbac.rbac0.dto.SessionDtos.SessionUser;
import com.rbac.rbac0.service.DepartmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
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

import static com.rbac.rbac0.controller.SessionInterceptor.ACTOR;
import static com.rbac.rbac0.dto.DepartmentDtos.MAX_ID;

@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final DepartmentService departments;

    public DepartmentController(DepartmentService departments) {
        this.departments = departments;
    }

    @GetMapping
    public DepartmentTree list(@RequestAttribute(ACTOR) SessionUser actor) {
        return departments.listTree(actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DepartmentView create(@RequestAttribute(ACTOR) SessionUser actor, @Valid @RequestBody DepartmentInput request) {
        return departments.create(actor, request);
    }

    @PatchMapping("/{departmentId}")
    public DepartmentView update(@RequestAttribute(ACTOR) SessionUser actor,
                                 @PathVariable @Positive @Max(MAX_ID) long departmentId,
                                 @Valid @RequestBody DepartmentUpdate request) {
        return departments.update(actor, departmentId, request);
    }
}
