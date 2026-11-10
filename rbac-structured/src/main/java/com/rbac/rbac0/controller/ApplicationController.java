package com.rbac.rbac0.controller;

import com.rbac.rbac0.dto.ApplicationDtos.ApplicationInput;
import com.rbac.rbac0.dto.ApplicationDtos.ApplicationPage;
import com.rbac.rbac0.dto.ApplicationDtos.ApplicationPermissions;
import com.rbac.rbac0.dto.ApplicationDtos.ApplicationView;
import com.rbac.rbac0.dto.ApplicationDtos.PermissionCreate;
import com.rbac.rbac0.dto.ApplicationDtos.PermissionUpdate;
import com.rbac.rbac0.dto.ApplicationDtos.PermissionView;
import com.rbac.rbac0.dto.SessionDtos.SessionUser;
import com.rbac.rbac0.service.ApplicationService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static com.rbac.rbac0.controller.SessionInterceptor.ACTOR;
import static com.rbac.rbac0.dto.DepartmentDtos.MAX_ID;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService applications;

    public ApplicationController(ApplicationService applications) {
        this.applications = applications;
    }

    @GetMapping
    public ApplicationPage list(@RequestAttribute(ACTOR) SessionUser actor,
                                @RequestParam(defaultValue = "1") @Positive int page,
                                @RequestParam(defaultValue = "20") @Positive @Max(100) int pageSize) {
        return applications.list(actor, page, pageSize);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationView create(@RequestAttribute(ACTOR) SessionUser actor, @Valid @RequestBody ApplicationInput request) {
        return applications.create(actor, request);
    }

    @PatchMapping("/{applicationId}")
    public ApplicationView rename(@RequestAttribute(ACTOR) SessionUser actor,
                                  @PathVariable @Positive @Max(MAX_ID) long applicationId,
                                  @Valid @RequestBody ApplicationInput request) {
        return applications.rename(actor, applicationId, request);
    }

    @GetMapping("/{applicationId}/permissions")
    public ApplicationPermissions permissionTree(@RequestAttribute(ACTOR) SessionUser actor,
                                                 @PathVariable @Positive @Max(MAX_ID) long applicationId) {
        return applications.permissionTree(actor, applicationId);
    }

    @PostMapping("/{applicationId}/permissions")
    @ResponseStatus(HttpStatus.CREATED)
    public PermissionView createPermission(@RequestAttribute(ACTOR) SessionUser actor,
                                           @PathVariable @Positive @Max(MAX_ID) long applicationId,
                                           @Valid @RequestBody PermissionCreate request) {
        return applications.createPermission(actor, applicationId, request);
    }

    @PatchMapping("/{applicationId}/permissions/{permissionId}")
    public PermissionView updatePermission(@RequestAttribute(ACTOR) SessionUser actor,
                                           @PathVariable @Positive @Max(MAX_ID) long applicationId,
                                           @PathVariable @Positive @Max(MAX_ID) long permissionId,
                                           @Valid @RequestBody PermissionUpdate request) {
        return applications.updatePermission(actor, applicationId, permissionId, request);
    }
}
