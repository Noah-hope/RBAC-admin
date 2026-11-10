package com.rbac.rbac0.controller;

import com.rbac.rbac0.dto.RoleDtos.RoleDetails;
import com.rbac.rbac0.dto.RoleDtos.RoleInput;
import com.rbac.rbac0.dto.RoleDtos.RolePage;
import com.rbac.rbac0.dto.SessionDtos.SessionUser;
import com.rbac.rbac0.service.RoleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.CodePointLength;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static com.rbac.rbac0.controller.SessionInterceptor.ACTOR;
import static com.rbac.rbac0.dto.DepartmentDtos.MAX_ID;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService roles;

    public RoleController(RoleService roles) {
        this.roles = roles;
    }

    @GetMapping
    public RolePage list(@RequestAttribute(ACTOR) SessionUser actor,
                         @RequestParam(defaultValue = "1") @Positive int page,
                         @RequestParam(defaultValue = "20") @Positive @Max(100) int pageSize,
                         @RequestParam(defaultValue = "") @CodePointLength(max = 100) String keyword) {
        return roles.list(actor, page, pageSize, keyword);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleDetails create(@RequestAttribute(ACTOR) SessionUser actor, @Valid @RequestBody RoleInput request) {
        return roles.create(actor, request);
    }

    @GetMapping("/{roleId}")
    public RoleDetails get(@RequestAttribute(ACTOR) SessionUser actor, @PathVariable @Positive @Max(MAX_ID) long roleId) {
        return roles.get(actor, roleId);
    }

    @PutMapping("/{roleId}")
    public RoleDetails update(@RequestAttribute(ACTOR) SessionUser actor, @PathVariable @Positive @Max(MAX_ID) long roleId,
                              @Valid @RequestBody RoleInput request) {
        return roles.update(actor, roleId, request);
    }
}
